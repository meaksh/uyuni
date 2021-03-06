/**
 * Copyright (c) 2014--2016 SUSE LLC
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package com.redhat.rhn.manager.content.test;

import com.redhat.rhn.domain.product.SUSEProduct;
import com.redhat.rhn.domain.product.SUSEProductChannel;
import com.redhat.rhn.domain.product.SUSEProductFactory;
import com.redhat.rhn.domain.product.test.SUSEProductTestUtils;
import com.redhat.rhn.domain.scc.SCCRepository;
import com.redhat.rhn.manager.content.ContentSyncManager;
import com.redhat.rhn.manager.content.MgrSyncProductDto;
import com.redhat.rhn.testing.BaseTestCaseWithUser;
import com.redhat.rhn.testing.TestUtils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.suse.mgrsync.XMLChannel;
import com.suse.mgrsync.XMLChannels;
import com.suse.scc.model.SCCProduct;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.simpleframework.xml.core.Persister;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Tests for {@link ContentSyncManager} against expected output from previous
 * tool, mgr-ncc-sync.
 */
public class ContentSyncManagerNonRegressionTest extends BaseTestCaseWithUser {
    private static final String JARPATH = "/com/redhat/rhn/manager/content/test/";

    // generated by https://gitlab.suse.de/galaxy/ncc-sync-template-creator
    private static final String CHANNELS_XML = JARPATH + "channels.xml";
    private static final String UPGRADE_PATHS_XML = JARPATH + "upgrade_paths.xml";

    // generated by refresh_scc_test_data.rb
    private static final String PRODUCTS_JSON = JARPATH + "products.json";
    private static final String REPOSITORIES_JSON = JARPATH + "repositories.json";

    // manually compiled (eg. with LibreOffice)
    private static final String EXPECTED_PRODUCTS_CSV = JARPATH + "expected_products.csv";

    /** Logger instance. */
    private static Logger logger = Logger
            .getLogger(ContentSyncManagerNonRegressionTest.class);

    /** The failure strings. */
    private List<String> failures = new LinkedList<>();

    /**
     * Tests listProducts() against known correct output (originally from
     * mgr-ncc-sync).
     * @throws Exception if anything goes wrong
     */
    @SuppressWarnings("unchecked")
    public void testListProducts() throws Exception {
        File channelsXML = new File(TestUtils.findTestData(CHANNELS_XML).getPath());
        File productsJSON = new File(TestUtils.findTestData(PRODUCTS_JSON).getPath());
        File repositoriesJSON = new File(TestUtils.findTestData(REPOSITORIES_JSON)
                .getPath());
        File expectedProductsCSV =
                new File(TestUtils.findTestData(EXPECTED_PRODUCTS_CSV).getPath());
        File upgradePathsXML = new File(
                TestUtils.findTestData(UPGRADE_PATHS_XML).getPath());
        try {
            // clear existing products
            SUSEProductTestUtils.clearAllProducts();

            List<XMLChannel> allChannels =
                    new Persister().read(XMLChannels.class, channelsXML).getChannels();

            // explicitly "mark" OES repos to be mirrorable
            // this avoids querying NCC at test time (see ContentSyncManager.isMirrorable())
            for (XMLChannel channel : allChannels) {
                if (channel.getFamily().equals(ContentSyncManager.OES_CHANNEL_FAMILY)) {
                    channel.setSourceUrl(null);
                }
            }

            List<SCCProduct> sccProducts =
                    new Gson().fromJson(FileUtils.readFileToString(productsJSON),
                    new TypeToken<List<SCCProduct>>() { } .getType());

            ContentSyncManager csm = new ContentSyncManager();
            csm.setUpgradePathsXML(upgradePathsXML);

            // load repository data
            List<SCCRepository> sccRepositories =
                    new Gson().fromJson(FileUtils.readFileToString(repositoriesJSON),
                    new TypeToken<List<SCCRepository>>() { } .getType());
            csm.refreshRepositoriesCache(sccRepositories);

            // HACK: some SCC products do not have correct data
            // to be removed when SCC team fixes this
            csm.addDirtyFixes(sccProducts);

            csm.updateSUSEProducts(sccProducts);
            Collection<MgrSyncProductDto> baseProducts =
                    csm.listProducts(csm.getAvailableChannels(allChannels));

            Collection<MgrSyncProductDto> products = Stream.concat(
                    baseProducts.stream(),
                    baseProducts.stream()
                        .flatMap(p -> p.getExtensions().stream())
            ).collect(Collectors.toList());

            Set<MgrSyncProductDto> checkedProducts = new LinkedHashSet<>();

            List<String> lines = (List<String>) FileUtils.readLines(expectedProductsCSV);
            List<ExpectedProductDto> expectedProducts = lines.stream()
                .map(l -> Arrays.asList(l.split(",")))
                    .map(a -> new ExpectedProductDto(a.get(0), a.get(1), a.get(2),
                            a.get(3).equals("base"), a.subList(4, a.size())))
                    .collect(Collectors.toList());

            Map<ExpectedProductDto, ExpectedProductDto> parents =
                IntStream.range(0, expectedProducts.size())
                .boxed()
                .collect(Collectors.toMap(expectedProducts::get, i -> {
                    for (int j = i; j >= 0; j--) {
                        if (expectedProducts.get(j).isBase()) {
                            return expectedProducts.get(j);
                        }
                    }
                    return null;
                }));

            for (ExpectedProductDto ep : expectedProducts) {
                Optional<MgrSyncProductDto> actual = products.stream()
                    .filter(p ->
                        p.getFriendlyName().equals(ep.getName()) &&
                        p.getVersion().equals(ep.getVersion()) &&
                        p.getArch().equals(ep.getArch()) &&
                        parents.get(ep).getChannelLabels().contains(
                                toChannelLabel(p.getBaseChannel()))
                    )
                    .findFirst();

                if (actual.isPresent()) {
                    MgrSyncProductDto product = actual.get();
                    checkedProducts.add(product);

                    boolean actualBase = product.getChannels().stream().anyMatch(c ->
                        c.getParent().equals(ContentSyncManager.BASE_CHANNEL));
                    if (actualBase != ep.isBase()) {
                        failures.add("Product " + product.toString() + " should be " +
                            (ep.isBase() ? "a base product" : "an extension product") +
                            " but it's not");
                    }

                    List<XMLChannel> unexpectedChannels = product.getChannels().stream()
                        .filter(c -> !ep.getChannelLabels().contains(toChannelLabel(c)))
                        .collect(Collectors.toList());

                    unexpectedChannels.forEach(c -> {
                        failures.add("Product " + product.toString() +
                                " has unexpected channel " + toChannelLabel(c));
                    });

                    List<String> missingChannels = ep.getChannelLabels().stream().filter(
                        label -> !product.getChannels().stream()
                            .anyMatch(c -> toChannelLabel(c).equals(label))
                    ).collect(Collectors.toList());

                    missingChannels.forEach(c -> {
                        failures.add("Product " + product.toString() +
                                " does not have expected channel " + c);
                    });
                }
                else {
                    failures.add("Product was expected but not found: " + ep +
                            "(base channel in " + parents.get(ep).getChannelLabels() + ")");
                    failures.add(">>> Maybe you need to request a Beta Key?");
                }
            }

            products.removeAll(checkedProducts);
            products.forEach(p -> {
                failures.add("Product was not expected: " + p);
            });
        }
        finally {
            SUSEProductTestUtils.deleteIfTempFile(expectedProductsCSV);
            SUSEProductTestUtils.deleteIfTempFile(productsJSON);
            SUSEProductTestUtils.deleteIfTempFile(channelsXML);
        }

        if (!failures.isEmpty()) {
            for (String string : failures) {
                java.lang.System.out.println(string);
                logger.error(string);
            }
            fail("See log for output");
        }
    }

    private String toChannelLabel(XMLChannel c) {
        return c.getLabel() + (c.isOptional() ? "" : "*");
    }

    /**
     * Checks that updateSUSEProducts() can be called multiple times in a row
     * without failing.
     * @throws Exception if anything goes wrong
     */
    public void testUpdateProductsMultipleTimes() throws Exception {
        File productsJSON = new File(TestUtils.findTestData(PRODUCTS_JSON).getPath());
        File upgradePathsXML = new File(
                TestUtils.findTestData(UPGRADE_PATHS_XML).getPath());
        try {
            // clear existing products
            SUSEProductTestUtils.clearAllProducts();

            List<SCCProduct> sccProducts =
                    new Gson().fromJson(FileUtils.readFileToString(productsJSON),
                    new TypeToken<List<SCCProduct>>() { } .getType());

            ContentSyncManager csm = new ContentSyncManager();
            csm.setUpgradePathsXML(upgradePathsXML);

            // HACK: some SCC products do not have correct data
            // to be removed when SCC team fixes this
            csm.addDirtyFixes(sccProducts);

            csm.updateSUSEProducts(sccProducts);
            csm.updateSUSEProducts(sccProducts);
        }
        finally {
            SUSEProductTestUtils.deleteIfTempFile(productsJSON);
        }
    }

}

/**
 * Copyright (c) 2012--2018 SUSE LLC
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

package com.redhat.rhn.domain.product;

import com.redhat.rhn.common.hibernate.HibernateFactory;
import com.redhat.rhn.domain.rhnpackage.PackageArch;
import com.redhat.rhn.domain.rhnpackage.PackageFactory;
import com.redhat.rhn.domain.server.InstalledProduct;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Transformer;
import org.apache.log4j.Logger;
import org.hibernate.Criteria;
import org.hibernate.FetchMode;
import org.hibernate.Session;
import org.hibernate.criterion.Disjunction;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * SUSEProductFactory - the class used to fetch and store
 * {@link SUSEProduct} objects from the database.
 * @version $Rev$
 */
public class SUSEProductFactory extends HibernateFactory {

    private static Logger log = Logger.getLogger(SUSEProductFactory.class);
    private static SUSEProductFactory singleton = new SUSEProductFactory();

    private SUSEProductFactory() {
        super();
    }

    /**
     * Insert or update a SUSEProduct.
     * @param product SUSE product to be inserted into the database.
     */
    public static void save(SUSEProduct product) {
        singleton.saveObject(product);
    }

    /**
     * Insert or update a {@link SUSEProductChannel}.
     * @param productChannel SUSE product channel relationship to be inserted.
     */
    public static void save(SUSEProductChannel productChannel) {
        singleton.saveObject(productChannel);
    }

    /**
     * Insert or update a {@link SUSEUpgradePath}.
     * @param upgradePath upgrade path to be inserted.
     */
    public static void save(SUSEUpgradePath upgradePath) {
        singleton.saveObject(upgradePath);
    }

    /**
     * Insert or update a {@link SUSEProductExtension}.
     * @param productExtension migration target to be inserted.
     */
    public static void save(SUSEProductExtension productExtension) {
        singleton.saveObject(productExtension);
    }

    /**
     * Delete a {@link SUSEProduct} from the database.
     * @param product SUSE product to be deleted.
     */
    public static void remove(SUSEProduct product) {
        singleton.removeObject(product);
    }

    /**
     * Removes all products except the ones passed as parameter.
     * @param products products to keep
     */
    @SuppressWarnings("unchecked")
    public static void removeAllExcept(Collection<SUSEProduct> products) {
        Collection<Long> ids = CollectionUtils.collect(products, new Transformer() {
            @Override
            public Object transform(Object arg0In) {
                return ((SUSEProduct) arg0In).getId();
            }
        });

        Criteria c = getSession().createCriteria(SUSEProduct.class);
        c.add(Restrictions.not(Restrictions.in("id", ids)));

        for (SUSEProduct product : (List<SUSEProduct>) c.list()) {
            remove(product);
        }
    }

    /**
     * Lookup SUSEProductChannels by channel label
     * @param channelLabel the label
     * @return list of SUSEProductChannels
     */
    public static List<SUSEProductChannel> lookupByChannelLabel(String channelLabel) {
        Session session = HibernateFactory.getSession();
        return (List<SUSEProductChannel>)session.getNamedQuery("SUSEProduct.byChannel")
                .setParameter("label", channelLabel).list();
    }

    /**
     * return all mandatory channels
     * @param product the product
     * @param base the root product
     * @param baseChannelLabel the base channel label
     * @return stream of SUSEProductChannel
     */
    public static Stream<SUSEProductChannel> findAllMandatoryChannels(SUSEProduct product, SUSEProduct base,
            String baseChannelLabel) {
        Stream<SUSEProductChannel> concat = Stream.concat(
                product.getSuseProductChannels().stream().filter(
                        c -> baseChannelLabel.equals(c.getParentChannelLabel()) ||
                                baseChannelLabel.equals(c.getChannelLabel())
                ),
                SUSEProductFactory.findAllBaseProductsOf(product, base).stream().flatMap(
                        p -> findAllMandatoryChannels(p, base, baseChannelLabel)
                )
        );
        return concat;
    }

    /**
     * Finds the suse product channel for a given channel label.
     * Note: does not work for all channel labels see comment in source.
     * @param channelLabel channel label
     * @return suse product channel
     */
    public static Optional<SUSEProductChannel> findProductByChannelLabel(String channelLabel) {
        List<SUSEProductChannel> suseProducts = SUSEProductFactory.lookupByChannelLabel(channelLabel);
        if (suseProducts.isEmpty()) {
            return Optional.empty();
        }
        else {
            // We take the first item since there can be more then one entry.
            // All entries should point to the same "product" with only arch differences.
            // The only exception to this is sles11 sp1/2 but they are out of maintenance
            // and we decided to ignore this inconsistency until the great rewrite.
            return Optional.of(suseProducts.get(0));
        }
    }

    /**
     * Finds all mandetory channels for a given channel label.
     *
     * @param channelLabel channel label
     * @param archByChannelLabel a function mapping from channel label to architecture.
     *                           to filter out incompatible channels.
     * @return a stream of suse product channels which are required by the channel
     */
    public static Stream<SUSEProductChannel> findAllMandatoryChannels(String channelLabel,
            Function<String, String> archByChannelLabel) {
        return findProductByChannelLabel(channelLabel).map(suseProductChannel -> {
            Stream<SUSEProductChannel> suseProductChannelStream = Optional.ofNullable(
                    suseProductChannel.getParentChannelLabel()
            ).map(parentChannelLabel -> {
                SUSEProductChannel baseProductChannel = findProductByChannelLabel(parentChannelLabel).get();
                return findAllMandatoryChannels(
                        suseProductChannel.getProduct(),
                        baseProductChannel.getProduct(),
                        parentChannelLabel
                );
            }).orElseGet(() -> suseProductChannel.getProduct().getSuseProductChannels().stream());
            return Stream.concat(Stream.of(suseProductChannel), suseProductChannelStream).filter(s -> {
                return archByChannelLabel.apply(s.getChannelLabel()).equals(
                        archByChannelLabel.apply(suseProductChannel.getChannelLabel()));
            });
        }).orElse(Stream.empty());
    }

    /**
     * Merge all {@link SUSEUpgradePath} from existing ones
     * and the ones passed as parameter.
     * @param newUpgradePaths the new list of upgradePaths to keep stored
     */
    public static void mergeAllUpgradePaths(Collection<SUSEUpgradePath> newUpgradePaths) {
        List<SUSEUpgradePath> existingUpgradePaths = findAllSUSEUpgradePaths();
        for (SUSEUpgradePath upgradePath : existingUpgradePaths) {
            if (!newUpgradePaths.contains(upgradePath)) {
                SUSEProductFactory.remove(upgradePath);
            }
        }
        for (SUSEUpgradePath upgradePath : newUpgradePaths) {
            if (!existingUpgradePaths.contains(upgradePath)) {
                SUSEProductFactory.save(upgradePath);
            }
        }
    }

    /**
     * Merge all {@link SUSEProductExtension} from existing ones
     * and the ones passed as parameter.
     * @param newExtensions the new list of ProductExtensions to keep stored
     */
    public static void mergeAllProductExtension(List<SUSEProductExtension> newExtensions) {
        List<SUSEProductExtension> existingExtensions = findAllSUSEProductExtensions();
        for (SUSEProductExtension existingExtension : existingExtensions) {
            if (!newExtensions.contains(existingExtension)) {
                SUSEProductFactory.remove(existingExtension);
            }
            else {
                SUSEProductExtension newExtension = newExtensions.get(newExtensions.indexOf(existingExtension));
                if (newExtension.isRecommended() != existingExtension.isRecommended()) {
                    existingExtension.setRecommended(newExtension.isRecommended());
                    SUSEProductFactory.save(existingExtension);
                }
            }
        }
        for (SUSEProductExtension newExtension : newExtensions) {
            if (!existingExtensions.contains(newExtension)) {
                SUSEProductFactory.save(newExtension);
            }
        }
    }

    /**
     * Delete a {@link SUSEProductChannel} from the database.
     * @param productChannel SUSE product channel relationship to be deleted.
     */
    public static void remove(SUSEProductChannel productChannel) {
        singleton.removeObject(productChannel);
    }

    /**
     * Delete a {@link SUSEUpgradePath} from the database.
     * @param upgradePath upgrade path to be deleted.
     */
    public static void remove(SUSEUpgradePath upgradePath) {
        singleton.removeObject(upgradePath);
    }

    /**
     * Delete a {@link SUSEProductExtension} from the database.
     * @param productExtension productExtension to be deleted.
     */
    public static void remove(SUSEProductExtension productExtension) {
        singleton.removeObject(productExtension);
    }

    /**
     * Find a {@link SUSEProduct} given by name, version, release and arch.
     * @param name name
     * @param version version or null
     * @param release release or null
     * @param arch arch or null
     * @param imprecise if true, allow returning products with NULL name, version or
     * release even if the corresponding parameters are not null
     * @return product or null if it is not found
     */
    @SuppressWarnings("unchecked")
    public static SUSEProduct findSUSEProduct(String name, String version, String release,
            String arch, boolean imprecise) {

        Criteria c = getSession().createCriteria(SUSEProduct.class);
        c.add(Restrictions.eq("name", name.toLowerCase()));

        Disjunction versionCriterion = Restrictions.disjunction();
        if (imprecise || version == null) {
            versionCriterion.add(Restrictions.isNull("version"));
        }
        if (version != null) {
            versionCriterion.add(Restrictions.eq("version", version.toLowerCase()));
        }
        c.add(versionCriterion);

        Disjunction releaseCriterion = Restrictions.disjunction();
        if (imprecise || release == null) {
            releaseCriterion.add(Restrictions.isNull("release"));
        }
        if (release != null) {
            releaseCriterion.add(Restrictions.eq("release", release.toLowerCase()));
        }
        c.add(releaseCriterion);

        Disjunction archCriterion = Restrictions.disjunction();
        if (imprecise || arch == null) {
            archCriterion.add(Restrictions.isNull("arch"));
        }
        if (arch != null) {
            archCriterion.add(Restrictions.eq("arch",
                    PackageFactory.lookupPackageArchByLabel(arch)));
        }
        c.add(archCriterion);

        c.addOrder(Order.asc("name")).addOrder(Order.asc("version"))
                .addOrder(Order.asc("release")).addOrder(Order.asc("arch"));

        List<SUSEProduct> result = c.list();
        return result.isEmpty() ? null : result.get(0);
    }

    /**
     * Lookup a {@link SUSEProduct} by a given ID.
     * @param id the id to search for
     * @return the product found
     */
    public static SUSEProduct getProductById(Long id) {
        Session session = HibernateFactory.getSession();
        SUSEProduct p = (SUSEProduct) session.get(SUSEProduct.class, id);
        return p;
    }

    /**
     * Lookup a {@link SUSEProduct} object for given productId.
     * @param productId the product
     * @return SUSE product for given productId
     */
    public static SUSEProduct lookupByProductId(long productId) {
        Session session = getSession();
        Criteria c = session.createCriteria(SUSEProduct.class);
        c.add(Restrictions.eq("productId", productId));
        return (SUSEProduct) c.uniqueResult();
    }

    /**
     * Lookup all {@link SUSEProduct} objects and provide a map with productId as key.
     * @return map of SUSE products with productId as key
     */
    public static Map<Long, SUSEProduct> productsByProductIds() {
        Session session = getSession();
        Criteria c = session.createCriteria(SUSEProduct.class);
        Map<Long, SUSEProduct> result = new HashMap<>();
        for (SUSEProduct prd: (List<SUSEProduct>) c.list()) {
            result.put(prd.getProductId(), prd);
        }
        return result;
    }

    /**
     * Return all {@link SUSEProductExtension} which are recommended
     * @return SUSEProductExtensions which are recommended
     */
    public static List<SUSEProductExtension> allRecommendedExtensions() {
        CriteriaBuilder builder = getSession().getCriteriaBuilder();
        CriteriaQuery<SUSEProductExtension> criteria = builder.createQuery(SUSEProductExtension.class);
        Root<SUSEProductExtension> root = criteria.from(SUSEProductExtension.class);
        criteria.where(builder.equal(root.get("recommended"), true));
        return getSession().createQuery(criteria).getResultList();
    }

    /**
     * Find all {@link SUSEProductChannel} relationships.
     * @return list of SUSE product channel relationships
     */
    @SuppressWarnings("unchecked")
    public static List<SUSEProductChannel> findAllSUSEProductChannels() {
        Session session = getSession();
        Criteria c = session.createCriteria(SUSEProductChannel.class);
        return c.list();
    }

    /**
     * Find SUSE Product Channel by label and product_id from the product table.
     * @param channelLabel the label of the channel.
     * @param productId product id.
     * @return SUSE Product Channel if it is there.
     */
    public static SUSEProductChannel lookupSUSEProductChannel(
            String channelLabel, Long productId) {

        Criteria c = HibernateFactory.getSession()
                .createCriteria(SUSEProductChannel.class)
                .add(Restrictions.eq("channelLabel", channelLabel))
                .createCriteria("product")
                .add(Restrictions.eq("productId", productId))
                .setFetchMode("product", FetchMode.SELECT);

        return (SUSEProductChannel) c.uniqueResult();
    }

    /**
     * Find all {@link SUSEUpgradePath}.
     * @return list of upgrade paths
     */
    @SuppressWarnings("unchecked")
    public static List<SUSEUpgradePath> findAllSUSEUpgradePaths() {
        Session session = getSession();
        Criteria c = session.createCriteria(SUSEUpgradePath.class);
        return c.list();
    }

    /**
     * Find a {@link SUSEUpgradePath} given by source and target {@link SUSEProduct}s.
     * @param fromProduct the source product
     * @param toProduct the target product
     * @return SUSEUpgradePath if it is there
     */
    public static SUSEUpgradePath findSUSEUpgradePath(SUSEProduct fromProduct,
            SUSEProduct toProduct) {
        Session session = getSession();

        Criteria c = session.createCriteria(SUSEUpgradePath.class)
                .add(Restrictions.eq("fromProduct", fromProduct))
                .add(Restrictions.eq("toProduct", toProduct));

        return (SUSEUpgradePath) c.uniqueResult();
    }

    /**
     * Find extensions for the product
     * @param root the root product
     * @param base the base product
     * @param ext the extension product
     * @return the Optional of {@link SUSEProductExtension} product
     */
    public static Optional<SUSEProductExtension> findSUSEProductExtension(SUSEProduct root,
                                                           SUSEProduct base,
                                                           SUSEProduct ext) {
        Session session = getSession();

        Criteria c = session.createCriteria(SUSEProductExtension.class)
                .add(Restrictions.eq("rootProduct", root))
                .add(Restrictions.eq("baseProduct", base))
                .add(Restrictions.eq("extensionProduct", ext));
        SUSEProductExtension result = (SUSEProductExtension) c.uniqueResult();
        if (result == null) {
            return Optional.empty();
        }
        else {
            return Optional.of(result);
        }
    }

    /**
     * Find all {@link SUSEProductExtension}.
     * @return list of product extension
     */
    @SuppressWarnings("unchecked")
    public static List<SUSEProductExtension> findAllSUSEProductExtensions() {
        Session session = getSession();
        Criteria c = session.createCriteria(SUSEProductExtension.class);
        return c.list();
    }

    /**
     * Find all {@link SUSEProductExtension} of a product.
     * @param base product to find extensions of
     * @return list of product extension of the given product
     */
    @SuppressWarnings("unchecked")
    public static List<SUSEProduct> findAllExtensionProductsOf(SUSEProduct base) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("baseId", base.getId());
        return singleton.listObjectsByNamedQuery("SUSEProductExtension.findAllExtensionProductsOf", params);
    }

    /**
     * Find all base products of a product.
     * @param ext product to find bases for
     * @return list of base products of the given product
     */
    @SuppressWarnings("unchecked")
    public static List<SUSEProduct> findAllBaseProductsOf(SUSEProduct ext) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("extId", ext.getId());
        return singleton.listObjectsByNamedQuery("SUSEProductExtension.findAllBaseProductsOf", params);
    }

    /**
     * Find all base products of a product in the tree of a specified root product.
     * @param ext product to find bases for
     * @param root the root product
     * @return list of base products of the given product and root
     */
    @SuppressWarnings("unchecked")
    public static List<SUSEProduct> findAllBaseProductsOf(SUSEProduct ext, SUSEProduct root) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("extId", ext.getId());
        params.put("rootId", root.getId());
        return singleton.listObjectsByNamedQuery("SUSEProductExtension.findAllBaseProductsForRootOf", params);
    }

    /**
     * Find all root products of a product.
     * @param base product to find roots for
     * @return list of root products of the given product
     */
    @SuppressWarnings("unchecked")
    public static List<SUSEProduct> findAllRootProductsOf(SUSEProduct base) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("baseId", base.getId());
        return singleton.listObjectsByNamedQuery("SUSEProductExtension.findAllRootProductsOf", params);
    }

    /**
     * Find all {@link SUSEProduct}.
     * @return list of all known products
     */
    @SuppressWarnings("unchecked")
    public static List<SUSEProduct> findAllSUSEProducts() {
        return getSession().createCriteria(SUSEProduct.class).list();
    }

    /**
     * Find an {@link InstalledProduct} given by name, version,
     * release, arch and isBaseProduct flag.
     * @param name name
     * @param version version
     * @param release release
     * @param arch arch
     * @param isBaseProduct is base product flag
     * @return installedProduct or null if it is not found
     */
    @SuppressWarnings("unchecked")
    public static Optional<InstalledProduct> findInstalledProduct(String name,
            String version, String release, PackageArch arch, boolean isBaseProduct) {

        Criteria c = getSession().createCriteria(InstalledProduct.class);
        c.add(Restrictions.eq("name", name));
        c.add(Restrictions.eq("version", version));
        c.add(Restrictions.eq("release", release));
        c.add(Restrictions.eq("arch", arch));
        c.add(Restrictions.eq("baseproduct", isBaseProduct));
        c.addOrder(Order.asc("name")).addOrder(Order.asc("version"))
                .addOrder(Order.asc("release")).addOrder(Order.asc("arch"));

        return c.list().stream().findFirst();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Logger getLogger() {
        return log;
    }
}

/**
 * Copyright (c) 2018 SUSE LLC
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

package com.suse.manager.webui.controllers;

import com.google.gson.reflect.TypeToken;
import com.redhat.rhn.domain.channel.ChannelFactory;
import com.redhat.rhn.domain.iss.IssFactory;
import com.redhat.rhn.domain.product.SUSEProduct;
import com.redhat.rhn.domain.product.SUSEProductFactory;
import com.redhat.rhn.domain.role.RoleFactory;
import com.redhat.rhn.domain.scc.SCCCachingFactory;
import com.redhat.rhn.domain.user.User;
import com.redhat.rhn.manager.content.ContentSyncException;
import com.redhat.rhn.manager.content.ContentSyncManager;
import com.redhat.rhn.manager.content.MgrSyncProductDto;
import com.redhat.rhn.manager.setup.ProductSyncManager;
import com.redhat.rhn.taskomatic.TaskoFactory;
import com.redhat.rhn.taskomatic.domain.TaskoRun;
import com.suse.manager.model.products.Extension;
import com.suse.manager.model.products.ChannelJson;
import com.suse.manager.model.products.Product;
import com.suse.manager.webui.utils.gson.ResultJson;
import com.suse.mgrsync.XMLChannel;
import com.suse.utils.Json;
import org.apache.log4j.Logger;
import spark.ModelAndView;
import spark.Request;
import spark.Response;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.suse.manager.webui.utils.SparkApplicationHelper.json;

/**
 * Controller class providing backend code for the Products
 */
public class ProductsController {

    private static final String ISS_MASTER = "issMaster";
    private static final String REFRESH_NEEDED = "refreshNeeded";
    private static final String REFRESH_RUNNING = "refreshRunning";

    private static Logger log = Logger.getLogger(ImageBuildController.class);

    private ProductsController() { }

    /**
     * Displays the Products page
     *
     * @param request the request
     * @param response the response
     * @param user the user
     * @return the ModelAndView object to render the page
     */
    public static ModelAndView show(Request request, Response response, User user) {
        TaskoRun latestRun = TaskoFactory.getLatestRun("mgr-sync-refresh-bunch");

        Map<String, Object> data = new HashMap<>();

        data.put(ISS_MASTER, String.valueOf(IssFactory.getCurrentMaster() == null));
        data.put(REFRESH_NEEDED, String.valueOf(SCCCachingFactory.refreshNeeded()));
        data.put(REFRESH_RUNNING, String.valueOf(latestRun != null && latestRun.getEndTime() == null));

        return new ModelAndView(data, "products/show.jade");
    }

    /**
     * Trigger a synchronization of Products
     *
     * @param request the request
     * @param response the response
     * @param user the user
     * @return a JSON flag of the success/failed result
     */
    public static String synchronizeProducts(Request request, Response response, User user) {
        if (!user.hasRole(RoleFactory.SAT_ADMIN)) {
            throw new IllegalArgumentException("Must be SAT_ADMIN to synchronize products");
        }
        try {
            ContentSyncManager csm = new ContentSyncManager();
            csm.updateSUSEProducts(csm.getProducts());
        }
        catch (Exception e) {
            log.fatal(e.getMessage(), e);
            return json(response, false);
        }
        return json(response, true);
    }

    /**
     * Trigger a synchronization of Channel Families
     *
     * @param request the request
     * @param response the response
     * @param user the user
     * @return a JSON flag of the success/failed result
     */
    public static String synchronizeChannelFamilies(Request request, Response response, User user) {
        if (!user.hasRole(RoleFactory.SAT_ADMIN)) {
            throw new IllegalArgumentException("Must be SAT_ADMIN to synchronize products");
        }
        try {
            ContentSyncManager csm = new ContentSyncManager();
            csm.updateChannelFamilies(csm.readChannelFamilies());
        }
        catch (Exception e) {
            log.fatal(e.getMessage(), e);
            return json(response, false);
        }
        return json(response, true);
    }

    /**
     * Trigger a synchronization of Channels
     *
     * @param request the request
     * @param response the response
     * @param user the user
     * @return a JSON flag of the success/failed result
     */
    public static String synchronizeChannels(Request request, Response response, User user) {
          if (!user.hasRole(RoleFactory.SAT_ADMIN)) {
            throw new IllegalArgumentException("Must be SAT_ADMIN to synchronize products");
          }
          try {
            ContentSyncManager csm = new ContentSyncManager();
            csm.updateChannels(null);
        }
        catch (Exception e) {
            log.fatal(e.getMessage(), e);
            return json(response, false);
        }
        return json(response, true);
    }

    /**
     * Trigger a synchronization of Subscriptions
     *
     * @param request the request
     * @param response the response
     * @param user the user
     * @return a JSON flag of the success/failed result
     */
    public static String synchronizeSubscriptions(Request request, Response response, User user) {
        try {
            ContentSyncManager csm = new ContentSyncManager();
            csm.updateSubscriptions(csm.getSubscriptions());
        }
        catch (Exception e) {
            log.fatal(e.getMessage(), e);
            return json(response, false);
        }
        return json(response, true);
    }

    /**
     * Trigger a synchronization of Product Channels
     *
     * @param request the request
     * @param response the response
     * @param user the user
     * @return a JSON flag of the success/failed result
     */
    public static String synchronizeProductChannels(Request request, Response response, User user) {
        if (!user.hasRole(RoleFactory.SAT_ADMIN)) {
            throw new IllegalArgumentException("Must be SAT_ADMIN to synchronize products");
        }
        try {
            ContentSyncManager csm = new ContentSyncManager();
            csm.updateSUSEProductChannels(csm.getAvailableChannels(csm.readChannels()));
        }
        catch (Exception e) {
            log.fatal(e.getMessage(), e);
            return json(response, false);
        }
        return json(response, true);
    }

    /**
     * Add products to be mirrored in the SUSE Manager Server
     *
     * @param request the request
     * @param response the response
     * @param user the user
     * @return the map of requested products with a success/failed flag in a JSON format
     */
    public static String addProduct(Request request, Response response, User user) {
        List<String> identifiers = Json.GSON.fromJson(request.body(), new TypeToken<List<String>>() { }.getType());
        if (!user.hasRole(RoleFactory.SAT_ADMIN)) {
            throw new IllegalArgumentException("Must be SAT_ADMIN to synchronize products");
        }
        if (log.isDebugEnabled()) {
            log.debug("Add/Sync products: " + identifiers);
        }
        Map<String, Optional<? extends Throwable>> stringOptionalMap =
                new ProductSyncManager().addProducts(identifiers, user);

        stringOptionalMap.forEach((k, v) -> {
            v.ifPresent(error -> {
                log.fatal("addProduct failed for " + k, error);
            });
        });

        Map<String, Boolean> collect = stringOptionalMap.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                kv -> kv.getValue().isPresent()
        ));
        return json(response, collect);
    }

    /**
     * Return Mandatory Channels
     *
     * @param request the request
     * @param response the response
     * @param user the user
     * @return the collection of Mandatory Channels in a JSON format
     */
    public static String getMandatoryChannels(Request request, Response response, User user) {
        List<Long> channelIdList = Json.GSON.fromJson(request.body(), new TypeToken<List<Long>>() { }.getType());
        ContentSyncManager csm = new ContentSyncManager();
        try {
            Map<String, String> archByChannelLabel = csm.listChannels().stream().collect(Collectors.toMap(
                    XMLChannel::getLabel,
                    XMLChannel::getArch
            ));
            Map<Long, List<Long>> result = channelIdList.stream().collect(Collectors.toMap(
                    channelId -> channelId,
                    channelId -> SUSEProductFactory.findAllMandatoryChannels(
                            ChannelFactory.lookupById(channelId).getLabel(), archByChannelLabel::get)
                                .filter(productChannel -> productChannel.getChannel() != null)
                                .map(productChannel -> productChannel.getChannel().getId())
                                .collect(Collectors.toList())
            ));

            return json(response, ResultJson.success(result));
        }
        catch (ContentSyncException e) {
            log.error("content sync error: ", e);
            return json(response, ResultJson.error("content sync error. See logs for more info."));
        }
    }
    /**
     * Returns JSON data for the SUSE products
     *
     * @param request the request
     * @param response the response
     * @param user the user
     * @return JSON result of the API call
     */
    public static String data(Request request, Response response, User user) {
        Map<String, Object> data = new HashMap<>();
        try {
            ContentSyncManager csm = new ContentSyncManager();
            ProductSyncManager psm = new ProductSyncManager();
            Collection<MgrSyncProductDto> products = csm.listProducts(csm.listChannels());

            List<Product> jsonProducts = products.stream().map(syncProduct -> {
                SUSEProduct rootProduct = SUSEProductFactory.lookupByProductId(syncProduct.getId());
                HashSet<Extension> rootExtensions = new HashSet<>();

                Map<Long, Extension> extensionByProductId = syncProduct.getExtensions().stream()
                        .collect(Collectors.toMap(MgrSyncProductDto::getId, s -> new Extension(
                                s.getId(),
                                s.getIdent(),
                                s.getFriendlyName(),
                                s.getArch(),
                                s.isRecommended(),
                                s.getStatus(),
                                s.getChannels().stream().map(c ->
                                        new ChannelJson(
                                                c.getName(),
                                                c.getLabel(),
                                                c.getSummary(),
                                                c.getOptional(),
                                                psm.getChannelSyncStatus(c.getLabel()).getStage()
                                        )
                                ).collect(Collectors.toSet()),
                                new HashSet<>()
                        )));

                // recreate the extension tree from our flat representation
                for (MgrSyncProductDto ext : syncProduct.getExtensions()) {
                    long extProductId = ext.getId();
                    SUSEProduct extProduct = SUSEProductFactory.lookupByProductId(extProductId);
                    List<SUSEProduct> allBaseProductsOf = SUSEProductFactory
                            .findAllBaseProductsOf(extProduct, rootProduct);
                    if (allBaseProductsOf.isEmpty()) {
                        rootExtensions.add(extensionByProductId.get(extProductId));
                    }
                    for (SUSEProduct baseProduct : allBaseProductsOf) {
                        if (baseProduct.getProductId() == rootProduct.getProductId()) {
                            rootExtensions.add(extensionByProductId.get(extProductId));
                        }
                        else {
                            Optional.ofNullable(extensionByProductId.get(baseProduct.getProductId())).ifPresent(e -> {
                                e.getExtensions().add(extensionByProductId.get(extProductId));
                            });
                        }
                    }
                }

                return new Product(
                        syncProduct.getId(),
                        syncProduct.getIdent(),
                        syncProduct.getFriendlyName(),
                        syncProduct.getArch(),
                        syncProduct.isRecommended(),
                        syncProduct.getStatus(),
                        rootExtensions,
                        syncProduct.getChannels().stream().map(c ->
                                new ChannelJson(
                                        c.getName(),
                                        c.getLabel(),
                                        c.getSummary(),
                                        c.getOptional(),
                                        psm.getChannelSyncStatus(c.getLabel()).getStage()
                                )
                        ).collect(Collectors.toSet()));

            }).collect(Collectors.toList());
            data.put("baseProducts", jsonProducts);
        }
        catch (Exception e) {
            log.error("Exception while rendering products: " + e.getMessage());
            data.put("error", "Exception while fetching products: " + e.getMessage());
        }

        return json(response, data);
    }
}

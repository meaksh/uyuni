/**
 * Copyright (c) 2016 SUSE LLC
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
package com.redhat.rhn.domain.server;

import com.redhat.rhn.domain.channel.AccessToken;
import com.redhat.rhn.domain.config.ConfigChannel;
import com.redhat.rhn.domain.state.ServerStateRevision;
import com.redhat.rhn.domain.state.StateFactory;
import com.redhat.rhn.domain.user.User;

import com.suse.manager.webui.services.SaltStateGeneratorService;
import com.suse.manager.webui.services.StateRevisionService;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * MinionServer
 */
public class MinionServer extends Server {

    private String minionId;
    private String osFamily;
    private String kernelLiveVersion;
    private Set<AccessToken> accessTokens = new HashSet<>();

    /**
     * Constructs a MinionServer instance.
     */
    public MinionServer() {
        super();
    }

    /**
     * @return the minion id
     */
    public String getMinionId() {
        return minionId;
    }

    /**
     * @param minionIdIn the minion id to set
     */
    public void setMinionId(String minionIdIn) {
        this.minionId = minionIdIn;
    }

    /**
     * Getter for os family
     *
     * @return String to get
     */
    public String getOsFamily() {
        return this.osFamily;
    }

    /**
     * Setter for os family
     *
     * @param osFamilyIn to set
     */
    public void setOsFamily(String osFamilyIn) {
        this.osFamily = osFamilyIn;
    }

    /**
     * Gets kernel live version.
     *
     * @return the kernel live version
     */
    public String getKernelLiveVersion() {
        return kernelLiveVersion;
    }

    /**
     * Sets kernel live version.
     *
     * @param kernelLiveVersionIn the kernel live version
     */
    public void setKernelLiveVersion(String kernelLiveVersionIn) {
        this.kernelLiveVersion = kernelLiveVersionIn;
    }

    /**
     * @inheritDoc
     */
    @Override
    public void subscribeConfigChannels(List<ConfigChannel> configChannelList, User user) {
        super.subscribeConfigChannels(configChannelList, user);
        ServerStateRevision newServerRevision = StateRevisionService.INSTANCE
                .cloneLatest(this, user, true, true);
        newServerRevision.getConfigChannels().addAll(configChannelList);
        SaltStateGeneratorService.INSTANCE.generateServerCustomState(newServerRevision);
        StateFactory.save(newServerRevision);
    }

    /**
     * @inheritDoc
     */
    @Override
    public int unsubscribeConfigChannels(List<ConfigChannel> configChannelList,
            User user) {
        ServerStateRevision newServerRevision = StateRevisionService.INSTANCE
                .cloneLatest(this, user, true, true);
        newServerRevision.getConfigChannels().removeAll(configChannelList);
        SaltStateGeneratorService.INSTANCE.generateServerCustomState(newServerRevision);
        StateFactory.save(newServerRevision);
        return super.unsubscribeConfigChannels(configChannelList, user);
    }

    /**
     * @inheritDoc
     */
    @Override
    public void setConfigChannels(List<ConfigChannel> configChannelList, User user) {
        super.setConfigChannels(configChannelList, user);
        ServerStateRevision newServerRevision = StateRevisionService.INSTANCE
                .cloneLatest(this, user, true, false);
        newServerRevision.getConfigChannels().addAll(configChannelList);
        SaltStateGeneratorService.INSTANCE.generateServerCustomState(newServerRevision);
        StateFactory.save(newServerRevision);
    }

    /**
     * Return <code>true</code> if OS on this system supports Containerization,
     * <code>false</code> otherwise.
     * <p>
     * Note: For SLES, we are only checking if it's not 10 or 11.
     * Older than SLES 10 are not being checked.
     * </p>
     *
     * @return <code>true</code> if OS supports Containerization
     */
    @Override
    public boolean doesOsSupportsContainerization() {
        return !isSLES11Or10();
    }

    /**
     * @return true if the installer type is of SLES 11 or 10
     */
    private boolean isSLES11Or10() {
        return ServerConstants.SLES.equals(getOs()) &&
               (getRelease().startsWith("11") || getRelease().startsWith("10"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof MinionServer)) {
            return false;
        }
        MinionServer otherMinion = (MinionServer) other;
        return new EqualsBuilder()
                .appendSuper(super.equals(otherMinion))
                .append(getMachineId(), otherMinion.getMachineId())
                .append(getMinionId(), otherMinion.getMinionId())
                .isEquals();
    }

    /**
     * Get channel access tokens assigned to this minion.
     * @return set of access tokens
     */
    public Set<AccessToken> getAccessTokens() {
        return accessTokens;
    }

    /**
     * @deprecated do not used (its only here for hibernate)
     * @param accessTokensIn access token
     */
    @Deprecated
    public void setAccessTokens(Set<AccessToken> accessTokensIn) {
        this.accessTokens = accessTokensIn;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .appendSuper(super.hashCode())
                .append(getMachineId())
                .append(getMinionId())
                .toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .appendSuper(super.toString())
                .append("machineId", getMachineId())
                .append("minionId", getMinionId())
                .toString();
    }

    /**
     * Converts this server to a MinionServer if it is one.
     *
     * @return optional of MinionServer
     */
    public Optional<MinionServer> asMinionServer() {
        return Optional.of(this);
    }
}
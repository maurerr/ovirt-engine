package org.ovirt.engine.core.bll.hostdeploy;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.ovirt.engine.core.bll.CommandAssertUtils.checkSucceeded;
import static org.ovirt.engine.core.utils.MockConfigRule.mockConfig;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.ovirt.engine.core.bll.AbstractQueryTest;
import org.ovirt.engine.core.common.businessentities.VDS;
import org.ovirt.engine.core.common.businessentities.VDSType;
import org.ovirt.engine.core.common.config.ConfigValues;
import org.ovirt.engine.core.common.queries.IdQueryParameters;
import org.ovirt.engine.core.compat.Guid;
import org.ovirt.engine.core.compat.RpmVersion;
import org.ovirt.engine.core.dao.VdsDao;
import org.ovirt.engine.core.utils.MockConfigRule;

@RunWith(MockitoJUnitRunner.class)
public class GetoVirtISOsTest extends AbstractQueryTest<IdQueryParameters, GetoVirtISOsQuery<IdQueryParameters>> {

    private static final String AVAILABLE_OVIRT_ISO_VERSION = "RHEV Hypervisor - 6.2 - 20111010.0.el6";
    private static final String UNAVAILABLE_OVIRT_ISO_VERSION = "RHEV Hypervisor - 8.2 - 20111010.0.el6";

    @Mock
    private VdsDao vdsDao;

    @Override
    protected Set<MockConfigRule.MockConfigDescriptor<Object>> getExtraConfigDescriptors() {
        return new HashSet<>(Arrays.asList(
            mockConfig(ConfigValues.OvirtInitialSupportedIsoVersion, "2.5.5:5.8"),
            mockConfig(ConfigValues.OvirtIsoPrefix, "^ovirt-node-iso-([0-9].*)\\.iso$:^rhevh-([0-9].*)\\.iso$"),
            mockConfig(ConfigValues.OvirtNodeOS, "^ovirt.*$:^rhev.*$"),
            mockConfig(ConfigValues.DataDir, "/usr/share/engine"),
            mockConfig(ConfigValues.oVirtISOsRepositoryPath, "/usr/share/ovirt-node-iso:/usr/share/rhev-hypervisor"))
        );
    }

    @Test
    public void testQueryWithHostId() {
        Guid vdsId = Guid.newGuid();
        VDS vds = new VDS();
        vds.setId(vdsId);
        vds.setVdsType(VDSType.oVirtVintageNode);
        vds.setHostOs(AVAILABLE_OVIRT_ISO_VERSION);
        when(vdsDao.get(any())).thenReturn(vds);

        when(getQueryParameters().getId()).thenReturn(vdsId);

        getQuery().setInternalExecution(true);
        getQuery().executeCommand();

        checkSucceeded(getQuery(), true);
        checkReturnValueEmpty(getQuery());
    }

    @Test
    public void testQueryClusterLevel() {
        Guid vdsId = Guid.newGuid();
        VDS vds = new VDS();
        vds.setId(vdsId);
        vds.setVdsType(VDSType.oVirtVintageNode);
        vds.setHostOs(UNAVAILABLE_OVIRT_ISO_VERSION);
        when(vdsDao.get(any())).thenReturn(vds);

        when(getQueryParameters().getId()).thenReturn(vdsId);

        getQuery().setInternalExecution(true);
        getQuery().executeCommand();

        checkSucceeded(getQuery(), true);
        checkReturnValueEmpty(getQuery());
    }

    @Test
    public void testQueryWithNonExistingHostId() {
        when(getQueryParameters().getId()).thenReturn(Guid.newGuid());
        getQuery().setInternalExecution(true);
        getQuery().executeCommand();

        checkSucceeded(getQuery(), true);
        checkReturnValueEmpty(getQuery());
    }

    @Test
    public void testQueryWithoutHostId() {
        getQuery().setInternalExecution(true);
        getQuery().executeCommand();

        checkSucceeded(getQuery(), true);
        checkReturnValueEmpty(getQuery());
    }

    @Test
    public void testQueryMultiplePaths() {
        mcr.mockConfigValue(ConfigValues.oVirtISOsRepositoryPath, "src/test/resources/ovirt-isos:src/test/resources/rhev-isos");
        getQuery().setInternalExecution(true);
        getQuery().executeCommand();

        checkSucceeded(getQuery(), true);
        checkReturnValueEmpty(getQuery());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testPrefixChange() {
        mcr.mockConfigValue(ConfigValues.OvirtIsoPrefix, "a different prefix");
        getQuery().setInternalExecution(true);
        getQuery().executeCommand();

        checkSucceeded(getQuery(), true);
        checkReturnValueEmpty(getQuery());
    }

    @SuppressWarnings("unchecked")
    private static void checkReturnValueEmpty(GetoVirtISOsQuery<IdQueryParameters> query) {
        List<RpmVersion> isosList = query.getQueryReturnValue().getReturnValue();
        assertTrue(isosList.isEmpty());
    }
}

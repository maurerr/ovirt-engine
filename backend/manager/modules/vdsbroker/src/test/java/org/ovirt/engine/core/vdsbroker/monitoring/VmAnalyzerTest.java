package org.ovirt.engine.core.vdsbroker.monitoring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.ovirt.engine.core.common.AuditLogType;
import org.ovirt.engine.core.common.businessentities.Cluster;
import org.ovirt.engine.core.common.businessentities.VDS;
import org.ovirt.engine.core.common.businessentities.VDSStatus;
import org.ovirt.engine.core.common.businessentities.VM;
import org.ovirt.engine.core.common.businessentities.VMStatus;
import org.ovirt.engine.core.common.businessentities.VmDynamic;
import org.ovirt.engine.core.common.businessentities.VmExitStatus;
import org.ovirt.engine.core.common.businessentities.VmJob;
import org.ovirt.engine.core.common.businessentities.VmStatic;
import org.ovirt.engine.core.common.businessentities.VmStatistics;
import org.ovirt.engine.core.common.vdscommands.DestroyVmVDSCommandParameters;
import org.ovirt.engine.core.common.vdscommands.VDSCommandType;
import org.ovirt.engine.core.common.vdscommands.VDSParametersBase;
import org.ovirt.engine.core.common.vdscommands.VDSReturnValue;
import org.ovirt.engine.core.compat.Guid;
import org.ovirt.engine.core.dal.dbbroker.auditloghandling.AuditLogDirector;
import org.ovirt.engine.core.dal.dbbroker.auditloghandling.AuditLogableBase;
import org.ovirt.engine.core.dao.ClusterDao;
import org.ovirt.engine.core.dao.VdsDao;
import org.ovirt.engine.core.dao.VmDao;
import org.ovirt.engine.core.dao.VmDynamicDao;
import org.ovirt.engine.core.dao.VmJobDao;
import org.ovirt.engine.core.dao.VmStaticDao;
import org.ovirt.engine.core.dao.VmStatisticsDao;
import org.ovirt.engine.core.dao.network.VmNetworkInterfaceDao;
import org.ovirt.engine.core.vdsbroker.ResourceManager;
import org.ovirt.engine.core.vdsbroker.VdsManager;
import org.ovirt.engine.core.vdsbroker.VmManager;

@RunWith(Theories.class)
public class VmAnalyzerTest {

    @DataPoints
    public static VmTestPairs[] VMS = VmTestPairs.values();

    VmAnalyzer vmAnalyzer;

    @Mock
    private AuditLogDirector auditLogDirector;
    @Captor
    private ArgumentCaptor<AuditLogableBase> loggableCaptor;
    @Captor
    private ArgumentCaptor<AuditLogType> logTypeCaptor;
    @Captor
    private ArgumentCaptor<VDSCommandType> vdsCommandTypeCaptor;
    @Captor
    private ArgumentCaptor<VDSParametersBase> vdsParamsCaptor;
    @Mock
    private VmStatisticsDao vmStatisticsDao;
    @Mock
    private VmStaticDao vmStaticDao;
    @Mock
    private VmDynamicDao vmDynamicDao;
    @Mock
    private Cluster cluster;
    @Mock
    private ClusterDao clusterDao;
    @Mock
    private VdsDao vdsDao;
    @Mock
    private VDS srcHost;
    @Mock
    private VDS dstHost;
    @Mock
    private VdsManager vdsManager;
    @Mock
    private VmManager vmManager;
    @Mock
    private VDS vdsManagerVds;
    @Mock
    private ResourceManager resourceManager;
    @Mock
    private VmJobDao vmJobsDao;
    @Mock
    private VmDao vmDao;
    @Mock
    private VmNetworkInterfaceDao vmNetworkInterfaceDao;

    @Theory
    public void externalVMWhenMissingInDb(VmTestPairs data) {
        //given
        initMocks(data, false);
        mockVmStatic(false);
        mockVmNotInDb(data);
        //when
        assumeTrue(data.dbVm() == null);
        assumeTrue(data.vdsmVm() != null);
        //then
        vmAnalyzer.analyze();
        assertTrue(vmAnalyzer.isUnmanagedVm());
    }

    @Theory
    public void vmNotRunningOnHost(VmTestPairs data) {
        //given
        initMocks(data, false);
        //when
        assumeTrue(data.vdsmVm() == null);
        //then
        vmAnalyzer.analyze();
        assertTrue(vmAnalyzer.isMovedToDown());
    }

    @Theory
    public void proceedDownVmsNormalExistReason_MIGRATION_HANDOVER(VmTestPairs data) {
        //given
        initMocks(data, false);

        //when
        assumeNotNull(data.dbVm(), data.vdsmVm());
        assumeTrue(data.dbVm().getStatus() == VMStatus.MigratingFrom);
        assumeTrue(data.vdsmVm().getVmDynamic().getStatus() == VMStatus.Down);
        assumeTrue(data.vdsmVm().getVmDynamic().getExitStatus() == VmExitStatus.Normal);
        //then
        vmAnalyzer.analyze();
        verify(auditLogDirector, atLeastOnce()).log(loggableCaptor.capture(), logTypeCaptor.capture());
        verify(resourceManager, never()).removeAsyncRunningVm(data.dbVm().getId());
        verify(vmAnalyzer).runVdsCommand(vdsCommandTypeCaptor.capture(), vdsParamsCaptor.capture());
        assertEquals(data.dbVm().getDynamicData(), vmAnalyzer.getVmDynamicToSave());
        assertEquals(VDSCommandType.Destroy, vdsCommandTypeCaptor.getValue());
        assertEquals(DestroyVmVDSCommandParameters.class, vdsParamsCaptor.getValue().getClass());
    }

    @Theory
    public void proceedDownVmsNormalExistReason(VmTestPairs data) {
        //given
        initMocks(data, false);

        //when
        assumeNotNull(data.dbVm(), data.vdsmVm());
        assumeTrue(data.dbVm().getStatus() != VMStatus.MigratingFrom);
        assumeTrue(data.vdsmVm().getVmDynamic().getStatus() == VMStatus.Down);
        assumeTrue(data.vdsmVm().getVmDynamic().getExitStatus() == VmExitStatus.Normal);
        //then
        vmAnalyzer.analyze();
        verify(auditLogDirector, atLeastOnce()).log(loggableCaptor.capture(), logTypeCaptor.capture());
        verify(resourceManager).removeAsyncRunningVm(data.dbVm().getId());
        verify(vmAnalyzer).runVdsCommand(vdsCommandTypeCaptor.capture(), vdsParamsCaptor.capture());
        assertEquals(data.dbVm().getDynamicData(), vmAnalyzer.getVmDynamicToSave());
        assertTrue(logTypeCaptor.getAllValues().contains(AuditLogType.VM_DOWN));
        assertEquals(VDSCommandType.Destroy, vdsCommandTypeCaptor.getValue());
        assertEquals(vdsParamsCaptor.getValue().getClass(), DestroyVmVDSCommandParameters.class);
    }

    @Theory
    public void proceedDownVmsErrorExitReason(VmTestPairs data) {
        //given
        initMocks(data, false);
        //when
        assumeNotNull(data.dbVm(), data.vdsmVm());
        assumeTrue(data.vdsmVm().getVmDynamic().getStatus() == VMStatus.Down);
        assumeTrue(data.vdsmVm().getVmDynamic().getExitStatus() != VmExitStatus.Normal);
        //then
        vmAnalyzer.analyze();
        verify(auditLogDirector, atLeastOnce()).log(loggableCaptor.capture(), logTypeCaptor.capture());
        verify(resourceManager, atLeast(3)).isVmInAsyncRunningList(data.dbVm().getId());
        assertEquals(data.dbVm().getDynamicData(), vmAnalyzer.getVmDynamicToSave());
    }

    @Theory
    public void proceedWatchdogEvents(VmTestPairs data) {
        //given
        initMocks(data, true);
        //when
        assumeNotNull(data.dbVm(), data.vdsmVm());
        //then
        verify(auditLogDirector, atLeastOnce()).log(loggableCaptor.capture(), logTypeCaptor.capture());
        assertTrue(logTypeCaptor.getAllValues().contains(AuditLogType.WATCHDOG_EVENT));
    }

    @Theory
    public void proceedBalloonCheck(VmTestPairs data) {
        //given
        initMocks(data, true);
        //when
        assumeNotNull(data.dbVm(), data.vdsmVm());
        //then
        verify(auditLogDirector, atLeastOnce()).log(loggableCaptor.capture(), logTypeCaptor.capture());
        assertTrue(logTypeCaptor.getAllValues().contains(AuditLogType.WATCHDOG_EVENT));
    }

    @Theory
    public void vmNotRunningOnHostWithBalloonEnabled(VmTestPairs data) {
        //given
        initMocks(data, false);
        when(vdsManagerVds.isBalloonEnabled()).thenReturn(true);
        //when
        assumeTrue(data.vdsmVm() == null);
        //then
        vmAnalyzer.analyze();
        assertTrue(vmAnalyzer.isMovedToDown());
    }

    @Theory
    public void proceedGuaranteedMemoryCheck() {
        //TODO add tests here
    }

    @Theory
    public void updateRepository_MIGRATION_FROM(VmTestPairs data) {
        //given
        initMocks(data, true);
        //when
        assumeNotNull(data.dbVm(), data.vdsmVm());
        // when vm is migrating
        assumeTrue(data.vdsmVm().getVmDynamic().getStatus() == VMStatus.MigratingFrom);
        //then
        assertTrue(vmAnalyzer.isClientIpChanged());
        verify(resourceManager, never()).internalSetVmStatus(data.dbVm(), VMStatus.MigratingTo);
    }

    @Theory
    public void updateRepository_MIGRATION_FROM_TO_DOWN(VmTestPairs data) {
        //given
        initMocks(data, true);
        //when
        assumeNotNull(data.dbVm(), data.vdsmVm());
        // when vm ended migration
        assumeTrue(data.dbVm().getStatus() == VMStatus.MigratingFrom);
        assumeTrue(data.vdsmVm().getVmDynamic().getStatus() == VMStatus.Down);
        //then
        verify(resourceManager, times(1)).internalSetVmStatus(data.dbVm(), VMStatus.MigratingTo);
        assertEquals(data.dbVm().getDynamicData(), vmAnalyzer.getVmDynamicToSave());
        assertNotNull(vmAnalyzer.getVmStatisticsToSave());
        assertEquals(VmTestPairs.DST_HOST_ID, data.dbVm().getRunOnVds());
    }

    @Theory
    public void updateRepository_MIGRATION_FROM_TO_UP(VmTestPairs data) {
        //given
        initMocks(data, false);
        //when
        assumeNotNull(data.dbVm(), data.vdsmVm());
        // when migration failed
        assumeTrue(data.dbVm().getStatus() == VMStatus.MigratingFrom);
        assumeTrue(data.vdsmVm().getVmDynamic().getStatus() == VMStatus.Up);
        //then
        vmAnalyzer.analyze();
        verify(resourceManager, times(1)).removeVmFromDownVms(VmTestPairs.SRC_HOST_ID,
                data.vdsmVm().getVmDynamic().getId());
        assertEquals(data.dbVm().getDynamicData(), vmAnalyzer.getVmDynamicToSave());
        assertEquals(VmTestPairs.SRC_HOST_ID, data.dbVm().getRunOnVds());
        assertTrue(vmAnalyzer.isRerun());
        assertNull(data.dbVm().getMigratingToVds());
    }

    @Theory
    public void updateRepository_HA_VM_DOWN(VmTestPairs data) {
        //given
        initMocks(data, false);
        //when
        assumeNotNull(data.dbVm(), data.vdsmVm());
        // when migration failed
        assumeTrue(data.dbVm().getStatus() == VMStatus.Up);
        assumeTrue(data.dbVm().isAutoStartup());
        assumeTrue(data.vdsmVm().getVmDynamic().getStatus() == VMStatus.Down);
        //then
        vmAnalyzer.analyze();
        assertEquals(data.dbVm().getDynamicData(), vmAnalyzer.getVmDynamicToSave());
        assertNotNull(vmAnalyzer.getVmStatisticsToSave());
        assertEquals(VmTestPairs.SRC_HOST_ID, data.dbVm().getRunOnVds());
        assertNull(data.vdsmVm().getVmDynamic().getRunOnVds());
        assertFalse(vmAnalyzer.isRerun());
        assertTrue(vmAnalyzer.isAutoVmToRun());
        assertNull(data.dbVm().getMigratingToVds());
    }

    @Theory
    public void updateRepository_PERSIST_DST_UP_VMS(VmTestPairs data) {
        //given
        initMocks(data, false);
        //when
        assumeNotNull(data.vdsmVm());
        assumeTrue(data.vdsmVm().getVmDynamic().getRunOnVds() == VmTestPairs.DST_HOST_ID);
        assumeTrue(data.vdsmVm().getVmDynamic().getStatus() == VMStatus.Up);
        //then
        vmAnalyzer.analyze();
        assertNotNull(vmAnalyzer.getVmDynamicToSave());
        assertNotEquals(data.vdsmVm().getVmDynamic(), vmAnalyzer.getVmDynamicToSave());
    }

    @Theory
    public void updateRepository_PERSIST_ALL_VMS_EXCEPT_MIGRATING_TO(VmTestPairs data) {
        //given
        initMocks(data, false);
        //when
        assumeNotNull(data.vdsmVm());
        assumeTrue(data.vdsmVm().getVmDynamic().getRunOnVds() == VmTestPairs.DST_HOST_ID);
        assumeTrue(data.vdsmVm().getVmDynamic().getStatus() == VMStatus.MigratingTo);
        //then
        vmAnalyzer.analyze();
        assertNull(vmAnalyzer.getVmDynamicToSave());
    }

    @Theory
    public void prepareGuestAgentNetworkDevicesForUpdate() {
        // TODO add tests
    }

    @Before
    public void before() {
        for (VmTestPairs data: VmTestPairs.values()) {
            data.reset();
        }
        MockitoAnnotations.initMocks(this);
    }

    public void initMocks(VmTestPairs vmData, boolean run) {
        stubDaos();
        when(vdsManager.getVdsId()).thenReturn(VmTestPairs.SRC_HOST_ID);
        when(vdsManager.getClusterId()).thenReturn(VmTestPairs.CLUSTER_ID);
        when(vdsManager.getCopyVds()).thenReturn(vdsManagerVds);
        when(vmManager.isColdReboot()).thenReturn(false);
        when(resourceManager.getVdsManager(any(Guid.class))).thenReturn(vdsManager);
        // -- default behaviors --
        // dst host is up
        mockDstHostStatus(VDSStatus.Up);
        // dst VM is in DB under the same Guid
        mockVmInDbForDstVms(vmData);
        // -- end of behaviors --
        vmAnalyzer = spy(new VmAnalyzer(
                vmData.dbVm(),
                vmData.vdsmVm(),
                false,
                vdsManager,
                auditLogDirector,
                resourceManager,
                vmStaticDao,
                vmDynamicDao,
                vmDao,
                vmNetworkInterfaceDao,
                vdsDao,
                null,
                null));
        doReturn(vmManager).when(vmAnalyzer).getVmManager();
        VDSReturnValue vdsReturnValue = new VDSReturnValue();
        vdsReturnValue.setSucceeded(true);
        doReturn(vdsReturnValue).when(vmAnalyzer).runVdsCommand(any(VDSCommandType.class), any(VDSParametersBase.class));

        if (run) {
            vmAnalyzer.analyze();
        }
    }

    private void stubDaos() {
        mockStatistics();
        mockVmDynamic();
        mockVmStatic(true);
        mockVmJob();
        mockCluster();
        mockVdsDao();
    }

    private void mockStatistics() {
        when(vmStatisticsDao.get(any(Guid.class))).thenReturn(mock(VmStatistics.class));
    }

    private void mockVmDynamic() {
        when(vmDynamicDao.get(any(Guid.class))).thenReturn(mock(VmDynamic.class));
    }

    private void mockVmStatic(boolean stubExists) {
        Mockito.reset(vmStaticDao);
        when(vmStaticDao.get(any(Guid.class))).thenReturn(stubExists ? mock(VmStatic.class) : null);
    }

    private void mockVmJob() {
        Mockito.reset(vmJobsDao);
        when(vmJobsDao.get(any(Guid.class))).thenReturn(mock(VmJob.class));
    }

    private void mockCluster() {
        when(clusterDao.get(VmTestPairs.CLUSTER_ID)).thenReturn(cluster);
    }

    private void mockVdsDao() {
        when(vdsDao.get(VmTestPairs.SRC_HOST_ID)).thenReturn(srcHost);
        when(vdsDao.get(VmTestPairs.DST_HOST_ID)).thenReturn(dstHost);
    }

    private void mockDstHostStatus(VDSStatus status) {
        when(dstHost.getStatus()).thenReturn(status);
    }


    private void mockVmInDbForDstVms(VmTestPairs vmData) {
        if (vmData.dbVm() == null && vmData.vdsmVm() != null) {
            VM dbVm = vmData.createDbVm();
            when(vmDao.get(vmData.vdsmVm().getVmDynamic().getId()))
                    .thenReturn(dbVm);
        }
    }
    private void mockVmNotInDb(VmTestPairs vmData) {
        if (vmData.vdsmVm() != null) {
            when(vmDao.get(vmData.vdsmVm().getVmDynamic().getId()))
                    .thenReturn(null);
        }
    }
}

package com.csdn;

import com.csdn.connection.ConnectedVimServiceBase;
import com.csdn.connection.helpers.GetMOREF;
import com.csdn.connection.helpers.WaitForValues;
import com.vmware.vim25.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Connect extends ConnectedVimServiceBase {
    public Connect(String url, String userName, String password) {
        connection.setUrl(url);         //这里的url格式为https://vcenterip/sdk
        connection.setUsername(userName);   //vcenter的用户名
        connection.setPassword(password);  //对应的密码
        connection.connect();

        //检查是否登录成功
        System.out.println(connection.getServiceContent().getAbout().getInstanceUuid());
    }

    void getAllVirtualMachine() {
        try {
            //实例化getMOREFs，这行代码是否需要，取决于如何调用这个函数，如果是在其他类调用这个函数，则需要这行代码，否则不需要
            getMOREFs = new GetMOREF(connection);
            //获取数据中心的引用
            ManagedObjectReference dcMor = getMOREFs.inContainerByType(connection.getServiceContent().getRootFolder(),
                    "Datacenter").get("dataCenter");
            //获取数据中心下的所有主机
            Map<String, ManagedObjectReference> hosts =
                    getMOREFs.inContainerByType(dcMor, "HostSystem");
            //获取指定主机的引用
            ManagedObjectReference hostMor = hosts.get("172.17.7.254");
            //获取主机下的虚拟机数量
            Map<String, ManagedObjectReference> vms =
                        getMOREFs.inContainerByType(hostMor, "VirtualMachine");

            System.out.println(vms.size());
        } catch (InvalidPropertyFaultMsg invalidPropertyFaultMsg) {
            invalidPropertyFaultMsg.printStackTrace();
        } catch (RuntimeFaultFaultMsg runtimeFaultFaultMsg) {
            runtimeFaultFaultMsg.printStackTrace();
        }
    }


    void getVirtualMachineConfig() {
        //ServiceContent这个类跟mob首页的ServiceContent对应起来的，这里相当于拿到一个容器
        ManagedObjectReference propCol = connection.getServiceContent().getPropertyCollector();
        getMOREFs = new GetMOREF(connection);
        try {
            //根据虚拟机名称拿到对应的引用
            ManagedObjectReference vmRef = getMOREFs.vmByVMname("CentOS6.5", propCol);
            //解析虚拟机summary属性
            VirtualMachineSummary vmSummary = (VirtualMachineSummary)
                    getMOREFs.entityProps(vmRef, new String[]{"summary"}).get("summary");
            System.out.println(vmSummary.getConfig().getMemorySizeMB());
            System.out.println(vmSummary.getConfig().getNumCpu());
        } catch (InvalidPropertyFaultMsg invalidPropertyFaultMsg) {
            invalidPropertyFaultMsg.printStackTrace();
        } catch (RuntimeFaultFaultMsg runtimeFaultFaultMsg) {
            runtimeFaultFaultMsg.printStackTrace();
        }

    }


    void createDataStore() {
        String datastore = "datastore-NFS";
        String ESXiHost = "192.168.0.xx";             //Esxi服务器ip
        String clientHost = "192.168.0.xxx";           //需要映射目录的服务器ip
        String path = "/home/xxx";                   //映射的目录

        try {
            getMOREFs = new GetMOREF(connection);
            Map<String, ManagedObjectReference> hostList =
                    getMOREFs.inFolderByType(connection.getServiceContent().getRootFolder(),
                            "HostSystem");
            ManagedObjectReference hostMor = hostList.get(ESXiHost);
            if (hostMor != null) {
                HostConfigManager configMgr =
                        (HostConfigManager) getMOREFs.entityProps(hostMor,
                                new String[]{"configManager"}).get("configManager");
                ManagedObjectReference nwSystem = configMgr.getDatastoreSystem();
                HostNasVolumeSpec spec = new HostNasVolumeSpec();           //实例化一个主机卷配置对象
                spec.setType("NFS");
                spec.setAccessMode("readWrite");
                spec.setLocalPath(datastore);
                spec.setRemoteHost(clientHost);
                spec.setRemotePath(path);
                connection.getVimPort().createNasDatastore(nwSystem, spec);                 //调用Web Service接口创建datastore
                System.out.println("create NFS datastore success");
            } else {
                System.out.println("Host not found");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    void createVirtualMachine() {
        String dataCenterName = "datacenter-xxx";
        String hostname = "Esxi-xxx";
        String dataStore = "datastore-xxx";
        String virtualMachineName = "csdn";
        long vmMemory = 1024L;
        int numCpus = 2;
        String guestId = "centos64Guest";          //操作系统标志
        try {
            getMOREFs = new GetMOREF(connection);
            ManagedObjectReference dcmor = getMOREFs.inContainerByType(connection.getServiceContent().getRootFolder(),
                    "Datacenter").get(dataCenterName);

            ManagedObjectReference hostmor = getMOREFs.inContainerByType(dcmor, "HostSystem").get(
                    hostname);

            ManagedObjectReference crmor =
                    (ManagedObjectReference) getMOREFs.entityProps(hostmor,
                            new String[]{"parent"}).get("parent");

            ManagedObjectReference resourcepoolmor =
                    (ManagedObjectReference) getMOREFs.entityProps(crmor,
                            new String[]{"resourcePool"}).get("resourcePool");
            ManagedObjectReference vmFolderMor =
                    (ManagedObjectReference) getMOREFs.entityProps(dcmor,
                            new String[]{"vmFolder"}).get("vmFolder");

            //填充vmConfigSpec对象
            VirtualMachineConfigSpec vmConfigSpec =
                    createVmConfigSpec(dataStore, 1048576, crmor, hostmor);

            vmConfigSpec.setName(virtualMachineName);
            vmConfigSpec.setAnnotation("VirtualMachine Annotation");
            vmConfigSpec.setMemoryMB(vmMemory);
            vmConfigSpec.setNumCPUs(numCpus);
            vmConfigSpec.setGuestId(guestId);

            ManagedObjectReference taskmor =
                    connection.getVimPort().createVMTask(vmFolderMor, vmConfigSpec, resourcepoolmor, hostmor);
            if (getTaskResultAfterDone(taskmor)) {
                System.out.println("Success: Creating VM  - [ " + virtualMachineName + " ]");
            } else {
                String msg = "Failure: Creating [ " + virtualMachineName + "] VM";
                System.out.println(msg);
            }

        } catch (InvalidPropertyFaultMsg|RuntimeFaultFaultMsg|AlreadyExistsFaultMsg|DuplicateNameFaultMsg|
                FileFaultFaultMsg|InsufficientResourcesFaultFaultMsg |InvalidDatastoreFaultMsg|InvalidNameFaultMsg|
                InvalidStateFaultMsg|OutOfBoundsFaultMsg|VmConfigFaultFaultMsg|InvalidCollectorVersionFaultMsg
                invalidPropertyFaultMsg) {
            invalidPropertyFaultMsg.printStackTrace();
        }
    }

    VirtualMachineConfigSpec createVmConfigSpec(String datastoreName, int diskSizeKB,
                                                ManagedObjectReference computeResMor, ManagedObjectReference hostMor)
            throws RuntimeFaultFaultMsg, InvalidPropertyFaultMsg {

        ConfigTarget configTarget = getConfigTargetForHost(computeResMor, hostMor);
        List<VirtualDevice> defaultDevices = getDefaultDevices(computeResMor, hostMor);
        VirtualMachineConfigSpec configSpec = new VirtualMachineConfigSpec();
        String networkName = null;
        if (configTarget.getNetwork() != null) {
            for (int i = 0; i < configTarget.getNetwork().size(); i++) {
                VirtualMachineNetworkInfo netInfo =
                        configTarget.getNetwork().get(i);
                NetworkSummary netSummary = netInfo.getNetwork();
                if (netSummary.isAccessible()) {
                    networkName = netSummary.getName();
                    break;
                }
            }
        }
        ManagedObjectReference datastoreRef = null;
        if (datastoreName != null) {
            boolean flag = false;
            for (int i = 0; i < configTarget.getDatastore().size(); i++) {
                VirtualMachineDatastoreInfo vdsInfo =
                        configTarget.getDatastore().get(i);
                DatastoreSummary dsSummary = vdsInfo.getDatastore();
                if (dsSummary.getName().equals(datastoreName)) {
                    flag = true;
                    if (dsSummary.isAccessible()) {
                        datastoreRef = dsSummary.getDatastore();
                    } else {
                        throw new RuntimeException(
                                "Specified Datastore is not accessible");
                    }
                    break;
                }
            }
            if (!flag) {
                throw new RuntimeException("Specified Datastore is not Found");
            }
        } else {
            boolean flag = false;
            for (int i = 0; i < configTarget.getDatastore().size(); i++) {
                VirtualMachineDatastoreInfo vdsInfo =
                        configTarget.getDatastore().get(i);
                DatastoreSummary dsSummary = vdsInfo.getDatastore();
                if (dsSummary.isAccessible()) {
                    datastoreName = dsSummary.getName();
                    datastoreRef = dsSummary.getDatastore();
                    flag = true;
                    break;
                }
            }
            if (!flag) {
                throw new RuntimeException("No Datastore found on host");
            }
        }
        String datastoreVolume = getVolumeName(datastoreName);
        VirtualMachineFileInfo vmfi = new VirtualMachineFileInfo();
        vmfi.setVmPathName(datastoreVolume);
        configSpec.setFiles(vmfi);
        // Add a scsi controller
        int diskCtlrKey = 1;
        VirtualDeviceConfigSpec scsiCtrlSpec = new VirtualDeviceConfigSpec();
        scsiCtrlSpec.setOperation(VirtualDeviceConfigSpecOperation.ADD);
        VirtualLsiLogicController scsiCtrl = new VirtualLsiLogicController();      //这里的控制器要注意与系统兼容
        scsiCtrl.setBusNumber(0);
        scsiCtrlSpec.setDevice(scsiCtrl);
        scsiCtrl.setKey(diskCtlrKey);
        scsiCtrl.setSharedBus(VirtualSCSISharing.NO_SHARING);
        String ctlrType = scsiCtrl.getClass().getName();
        ctlrType = ctlrType.substring(ctlrType.lastIndexOf(".") + 1);

        // Find the IDE controller
        VirtualDevice ideCtlr = null;
        for (VirtualDevice device : defaultDevices) {
            if (device instanceof VirtualIDEController) {
                ideCtlr = device;
                break;
            }
        }

        // Add a floppy
        VirtualDeviceConnectInfo cInfo = new VirtualDeviceConnectInfo();
        cInfo.setConnected(false);
        cInfo.setStartConnected(false);
        VirtualDeviceConfigSpec floppySpec = new VirtualDeviceConfigSpec();
        floppySpec.setOperation(VirtualDeviceConfigSpecOperation.ADD);
        VirtualFloppy floppy = new VirtualFloppy();
        VirtualFloppyRemoteDeviceBackingInfo flpBacking =
                new VirtualFloppyRemoteDeviceBackingInfo();
        flpBacking.setDeviceName("要进行连接，请打开虚拟机电源，然后从“摘要”选项卡的“虚拟机硬件”面板中选择介质。");
        floppy.setBacking(flpBacking);
        floppy.setKey(3);
        floppy.setConnectable(cInfo);
        floppySpec.setDevice(floppy);

        // Add a cdrom based on a physical device
        VirtualDeviceConfigSpec cdSpec = null;

        if (ideCtlr != null) {
            cdSpec = new VirtualDeviceConfigSpec();
            cdSpec.setOperation(VirtualDeviceConfigSpecOperation.ADD);
            VirtualCdrom cdrom = new VirtualCdrom();
            VirtualCdromIsoBackingInfo cdDeviceBacking =
                    new VirtualCdromIsoBackingInfo();
            cdDeviceBacking.setDatastore(datastoreRef);
            cdDeviceBacking.setFileName("");
            cdrom.setBacking(cdDeviceBacking);
            cdrom.setKey(20);
            cdrom.setControllerKey(ideCtlr.getKey());
            cdrom.setUnitNumber(0);
            cdrom.setConnectable(cInfo);
            cdSpec.setDevice(cdrom);
        }

        // Create a new disk - file based - for the vm
        VirtualDeviceConfigSpec diskSpec;
        diskSpec = createVirtualDisk(datastoreName, diskCtlrKey, diskSizeKB);

        // Add a NIC. the network Name must be set as the device name to create the NIC.
        VirtualDeviceConfigSpec nicSpec = new VirtualDeviceConfigSpec();
        if (networkName != null) {
            nicSpec.setOperation(VirtualDeviceConfigSpecOperation.ADD);
            VirtualEthernetCard nic = new VirtualVmxnet3();              //推荐使用的网卡类型
            VirtualEthernetCardNetworkBackingInfo nicBacking =
                    new VirtualEthernetCardNetworkBackingInfo();
            nicBacking.setDeviceName(networkName);
            nic.setAddressType("generated");
            nic.setBacking(nicBacking);
            nic.setKey(4);
            nicSpec.setDevice(nic);
        }

        List<VirtualDeviceConfigSpec> deviceConfigSpec =
                new ArrayList<>();
        deviceConfigSpec.add(scsiCtrlSpec);
        deviceConfigSpec.add(floppySpec);
        deviceConfigSpec.add(diskSpec);
        if (ideCtlr != null) {
            deviceConfigSpec.add(cdSpec);
            deviceConfigSpec.add(nicSpec);
        } else {
            deviceConfigSpec = new ArrayList<>();
            deviceConfigSpec.add(nicSpec);
        }
        configSpec.getDeviceChange().addAll(deviceConfigSpec);
        return configSpec;
    }


    VirtualDeviceConfigSpec createVirtualDisk(String volName, int diskCtlrKey, int diskSizeKB) {
        String volumeName = getVolumeName(volName);
        VirtualDeviceConfigSpec diskSpec = new VirtualDeviceConfigSpec();

        diskSpec.setFileOperation(VirtualDeviceConfigSpecFileOperation.CREATE);
        diskSpec.setOperation(VirtualDeviceConfigSpecOperation.ADD);

        VirtualDisk disk = new VirtualDisk();
        VirtualDiskFlatVer2BackingInfo diskfileBacking =
                new VirtualDiskFlatVer2BackingInfo();

        diskfileBacking.setFileName(volumeName);
        diskfileBacking.setDiskMode("persistent");         //磁盘模式
        diskfileBacking.setEagerlyScrub(true);             //是否延迟置零，默认为厚置备
        disk.setKey(0);
        disk.setControllerKey(diskCtlrKey);
        disk.setUnitNumber(0);
        disk.setBacking(diskfileBacking);
        disk.setCapacityInKB(diskSizeKB);

        diskSpec.setDevice(disk);

        return diskSpec;
    }

    String getVolumeName(String volName) {
        String volumeName;
        if (volName != null && volName.length() > 0) {
            volumeName = "[" + volName + "]";
        } else {
            volumeName = "[Local]";
        }

        return volumeName;
    }

    /**
     * This method returns the ConfigTarget for a HostSystem.
     *
     * @param computeResMor A MoRef to the ComputeResource used by the HostSystem
     * @param hostMor       A MoRef to the HostSystem
     * @return Instance of ConfigTarget for the supplied
     *         HostSystem/ComputeResource
     * @throws Exception When no ConfigTarget can be found
     */
    ConfigTarget getConfigTargetForHost(
            ManagedObjectReference computeResMor, ManagedObjectReference hostMor)
            throws RuntimeFaultFaultMsg, InvalidPropertyFaultMsg {
        ManagedObjectReference envBrowseMor =
                (ManagedObjectReference) getMOREFs.entityProps(computeResMor,
                        new String[]{"environmentBrowser"}).get(
                        "environmentBrowser");
        ConfigTarget configTarget =
                connection.getVimPort().queryConfigTarget(envBrowseMor, hostMor);
        if (configTarget == null) {
            throw new RuntimeException("No ConfigTarget found in ComputeResource");
        }
        return configTarget;
    }

    /**
     * The method returns the default devices from the HostSystem.
     *
     * @param computeResMor A MoRef to the ComputeResource used by the HostSystem
     * @param hostMor       A MoRef to the HostSystem
     * @return Array of VirtualDevice containing the default devices for the
     *         HostSystem
     * @throws Exception
     */
    List<VirtualDevice> getDefaultDevices(
            ManagedObjectReference computeResMor, ManagedObjectReference hostMor)
            throws RuntimeFaultFaultMsg, InvalidPropertyFaultMsg {
        ManagedObjectReference envBrowseMor =
                (ManagedObjectReference) getMOREFs.entityProps(computeResMor,
                        new String[]{"environmentBrowser"}).get(
                        "environmentBrowser");
        VirtualMachineConfigOption cfgOpt =
                connection.getVimPort().queryConfigOption(envBrowseMor, null, hostMor);
        List<VirtualDevice> defaultDevs;
        if (cfgOpt == null) {
            throw new RuntimeException(
                    "No VirtualHardwareInfo found in ComputeResource");
        } else {
            List<VirtualDevice> lvds = cfgOpt.getDefaultDevice();
            if (lvds == null) {
                throw new RuntimeException("No Datastore found in ComputeResource");
            } else {
                defaultDevs = lvds;
            }
        }
        return defaultDevs;
    }

    /**
     * This method returns a boolean value specifying whether the Task is
     * succeeded or failed.
     *
     * @param task ManagedObjectReference representing the Task.
     * @return boolean value representing the Task result.
     * @throws InvalidCollectorVersionFaultMsg
     *
     * @throws RuntimeFaultFaultMsg
     * @throws InvalidPropertyFaultMsg
     */
    boolean getTaskResultAfterDone(ManagedObjectReference task)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg,
            InvalidCollectorVersionFaultMsg {

        boolean retVal = false;

        // info has a property - state for state of the task
        waitForValues = new WaitForValues(connection);
        Object[] result =
                waitForValues.wait(task, new String[]{"info.state", "info.error"},
                        new String[]{"state"}, new Object[][]{new Object[]{
                                TaskInfoState.SUCCESS, TaskInfoState.ERROR}});

        if (result[0].equals(TaskInfoState.SUCCESS)) {
            retVal = true;
        }
        if (result[1] instanceof LocalizedMethodFault) {
            throw new RuntimeException(
                    ((LocalizedMethodFault) result[1]).getLocalizedMessage());
        }
        return retVal;
    }
}

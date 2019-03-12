package com.csdn;

import com.csdn.connection.ConnectedVimServiceBase;
import com.vmware.vim25.*;

import java.util.*;


public class TargetConnect extends ConnectedVimServiceBase {
    ManagedObjectReference propCollectorRef = null;
    String[] meTree = {"ManagedEntity", "ComputeResource",
            "ClusterComputeResource", "Datacenter", "Folder", "HostSystem",
            "ResourcePool", "VirtualMachine"};
    String[] crTree = {"ComputeResource",
            "ClusterComputeResource"};
    String[] hcTree = {"HistoryCollector",
            "EventHistoryCollector", "TaskHistoryCollector"};
    public TargetConnect(String url, String userName, String password) {
        connection.setUrl(url);         //这里的url格式为https://vcenterip/sdk
        connection.setUsername(userName);   //vcenter的用户名
        connection.setPassword(password);  //对应的密码
        connection.connect();
        propCollectorRef = connection.getServiceContent().getPropertyCollector();
        //检查是否登录成功
        System.out.println(connection.getServiceContent().getAbout().getInstanceUuid());
    }


    public ServiceLocator getServiceLocator() {


        ServiceLocator serviceLocator = new ServiceLocator();
        ServiceLocatorCredential credential = new ServiceLocatorNamePassword();
        ((ServiceLocatorNamePassword) credential).setPassword(connection.getPassword());
        ((ServiceLocatorNamePassword) credential).setUsername(connection.getUsername());
        serviceLocator.setCredential(credential);
        serviceLocator.setInstanceUuid(connection.getServiceContent().getAbout().getInstanceUuid());
        serviceLocator.setSslThumbprint("A0:53:8D:E0:6A:F7:8D:9B:4D:1D:82:0A:F8:26:24:D2:1D:B4:E1:9E");
        serviceLocator.setUrl(connection.getUrl().substring(0, connection.getUrl().indexOf("/sdk")));

        return serviceLocator;
    }


    public VirtualMachineRelocateSpec getVirtualMachineRelocateSpec(String host, String dataStore, String resourcePool) {

        try {
            VirtualMachineRelocateSpec relocateSpec = new VirtualMachineRelocateSpec();

            ManagedObjectReference hMOR = getHostByHostName(host);
            List<DynamicProperty> datastoresSource =
                    getDynamicProarray(hMOR, "HostSystem", "datastore");
            ArrayOfManagedObjectReference dsSourceArr =
                    ((ArrayOfManagedObjectReference) (datastoresSource.get(0)).getVal());
            List<ManagedObjectReference> dsTarget =
                    dsSourceArr.getManagedObjectReference();
            ManagedObjectReference dsMOR = browseDSMOR(dsTarget, dataStore);
            if (dsMOR == null) {
                throw new IllegalArgumentException(" DataSource " + dataStore
                        + " Not Found.");
            }
            ManagedObjectReference poolMOR =
                    getDecendentMoRef(null, "ResourcePool", resourcePool);
            if (poolMOR == null) {
                throw new IllegalArgumentException(" Target Resource Pool "
                        + resourcePool + " Not Found.");
            }


            relocateSpec.setDatastore(dsMOR);
            relocateSpec.setHost(hMOR);
            relocateSpec.setPool(poolMOR);
            relocateSpec.setService(getServiceLocator());
            return relocateSpec;

        } catch (RuntimeFaultFaultMsg runtimeFaultFaultMsg) {
            runtimeFaultFaultMsg.printStackTrace();
        } catch (InvalidPropertyFaultMsg invalidPropertyFaultMsg) {
            invalidPropertyFaultMsg.printStackTrace();
        }

        return null;
    }


    /**
     * Get the ManagedObjectReference for an item under the specified root folder
     * that has the type and name specified.
     *
     * @param root a root folder if available, or null for default
     * @param type type of the managed object
     * @param name name to match
     * @return First ManagedObjectReference of the type / name pair found
     */
    ManagedObjectReference getDecendentMoRef(
            ManagedObjectReference root, String type, String name) throws RuntimeFaultFaultMsg, InvalidPropertyFaultMsg {
        if (name == null || name.length() == 0) {
            return null;
        }

        String[][] typeinfo = new String[][]{new String[]{type, "name"},};

        List<ObjectContent> ocary =
                getContentsRecursively(null, root, typeinfo, true);

        if (ocary == null || ocary.size() == 0) {
            return null;
        }

        ObjectContent oc = null;
        ManagedObjectReference mor = null;
        List<DynamicProperty> propary = null;
        String propval = null;
        boolean found = false;
        for (int oci = 0; oci < ocary.size() && !found; oci++) {
            oc = ocary.get(oci);
            mor = oc.getObj();
            propary = oc.getPropSet();

            propval = null;
            if (type == null || typeIsA(type, mor.getType())) {
                if (propary.size() > 0) {
                    propval = (String) propary.get(0).getVal();
                }
                found = propval != null && name.equals(propval);
            }
        }

        if (!found) {
            mor = null;
        }

        return mor;
    }


    boolean typeIsA(String searchType, String foundType) {
        if (searchType.equals(foundType)) {
            return true;
        } else if (searchType.equals("ManagedEntity")) {
            for (int i = 0; i < meTree.length; ++i) {
                if (meTree[i].equals(foundType)) {
                    return true;
                }
            }
        } else if (searchType.equals("ComputeResource")) {
            for (int i = 0; i < crTree.length; ++i) {
                if (crTree[i].equals(foundType)) {
                    return true;
                }
            }
        } else if (searchType.equals("HistoryCollector")) {
            for (int i = 0; i < hcTree.length; ++i) {
                if (hcTree[i].equals(foundType)) {
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * Retrieve content recursively with multiple properties. the typeinfo array
     * contains typename + properties to retrieve.
     *
     * @param collector a property collector if available or null for default
     * @param root      a root folder if available, or null for default
     * @param typeinfo  2D array of properties for each typename
     * @param recurse   retrieve contents recursively from the root down
     * @return retrieved object contents
     */
    List<ObjectContent> getContentsRecursively(
            ManagedObjectReference collector, ManagedObjectReference root,
            String[][] typeinfo, boolean recurse) throws RuntimeFaultFaultMsg, InvalidPropertyFaultMsg {
        if (typeinfo == null || typeinfo.length == 0) {
            return null;
        }

        ManagedObjectReference usecoll = collector;
        if (usecoll == null) {
            usecoll = connection.getServiceContent().getPropertyCollector();
        }

        ManagedObjectReference useroot = root;
        if (useroot == null) {
            useroot = connection.getServiceContent().getRootFolder();
        }

        List<SelectionSpec> selectionSpecs = null;
        if (recurse) {
            selectionSpecs = buildFullTraversal();
        }

        List<PropertySpec> propspecary = buildPropertySpecArray(typeinfo);
        ObjectSpec objSpec = new ObjectSpec();
        objSpec.setObj(useroot);
        objSpec.setSkip(Boolean.FALSE);
        objSpec.getSelectSet().addAll(selectionSpecs);
        List<ObjectSpec> objSpecList = new ArrayList<ObjectSpec>();
        objSpecList.add(objSpec);
        PropertyFilterSpec spec = new PropertyFilterSpec();
        spec.getPropSet().addAll(propspecary);
        spec.getObjectSet().addAll(objSpecList);
        List<PropertyFilterSpec> listpfs = new ArrayList<PropertyFilterSpec>();
        listpfs.add(spec);
        List<ObjectContent> listobjcont = retrievePropertiesAllObjects(listpfs);

        return listobjcont;
    }


    /**
     * This code takes an array of [typename, property, property, ...] and
     * converts it into a PropertySpec[]. handles case where multiple references
     * to the same typename are specified.
     *
     * @param typeinfo 2D array of type and properties to retrieve
     * @return Array of container filter specs
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    List<PropertySpec> buildPropertySpecArray(String[][] typeinfo) {
        // Eliminate duplicates
        HashMap<String, Set> tInfo = new HashMap<String, Set>();
        for (int ti = 0; ti < typeinfo.length; ++ti) {
            Set props = tInfo.get(typeinfo[ti][0]);
            if (props == null) {
                props = new HashSet<String>();
                tInfo.put(typeinfo[ti][0], props);
            }
            boolean typeSkipped = false;
            for (int pi = 0; pi < typeinfo[ti].length; ++pi) {
                String prop = typeinfo[ti][pi];
                if (typeSkipped) {
                    props.add(prop);
                } else {
                    typeSkipped = true;
                }
            }
        }

        // Create PropertySpecs
        ArrayList<PropertySpec> pSpecs = new ArrayList<PropertySpec>();
        for (Iterator<String> ki = tInfo.keySet().iterator(); ki.hasNext(); ) {
            String type = ki.next();
            PropertySpec pSpec = new PropertySpec();
            Set props = tInfo.get(type);
            pSpec.setType(type);
            pSpec.setAll(props.isEmpty() ? Boolean.TRUE : Boolean.FALSE);
            for (Iterator pi = props.iterator(); pi.hasNext(); ) {
                String prop = (String) pi.next();
                pSpec.getPathSet().add(prop);
            }
            pSpecs.add(pSpec);
        }

        return pSpecs;
    }


    ManagedObjectReference browseDSMOR(
            List<ManagedObjectReference> dsMOR, String targetDS) throws RuntimeFaultFaultMsg, InvalidPropertyFaultMsg {
        ManagedObjectReference dataMOR = null;
        if (dsMOR != null && dsMOR.size() > 0) {
            for (int i = 0; i < dsMOR.size(); i++) {
                DatastoreSummary ds = getDataStoreSummary(dsMOR.get(i));
                String dsname = ds.getName();
                if (dsname.equalsIgnoreCase(targetDS)) {
                    dataMOR = dsMOR.get(i);
                    break;
                }
            }
        }
        return dataMOR;
    }


    DatastoreSummary getDataStoreSummary(
            ManagedObjectReference dataStore) throws RuntimeFaultFaultMsg, InvalidPropertyFaultMsg {
        DatastoreSummary dataStoreSummary = new DatastoreSummary();
        PropertySpec propertySpec = new PropertySpec();
        propertySpec.setAll(Boolean.FALSE);
        propertySpec.getPathSet().add("summary");
        propertySpec.setType("Datastore");

        // Now create Object Spec
        ObjectSpec objectSpec = new ObjectSpec();
        objectSpec.setObj(dataStore);
        objectSpec.setSkip(Boolean.FALSE);
        objectSpec.getSelectSet().addAll(buildFullTraversal());
        // Create PropertyFilterSpec using the PropertySpec and ObjectPec
        // created above.
        PropertyFilterSpec propertyFilterSpec = new PropertyFilterSpec();
        propertyFilterSpec.getPropSet().add(propertySpec);
        propertyFilterSpec.getObjectSet().add(objectSpec);
        List<PropertyFilterSpec> listpfs = new ArrayList<PropertyFilterSpec>(1);
        listpfs.add(propertyFilterSpec);
        List<ObjectContent> listobjcont = retrievePropertiesAllObjects(listpfs);
        for (int j = 0; j < listobjcont.size(); j++) {
            List<DynamicProperty> propSetList = listobjcont.get(j).getPropSet();
            for (int k = 0; k < propSetList.size(); k++) {
                dataStoreSummary = (DatastoreSummary) propSetList.get(k).getVal();
            }
        }
        return dataStoreSummary;
    }


    List<DynamicProperty> getDynamicProarray(
            ManagedObjectReference ref, String type, String propertyString) throws RuntimeFaultFaultMsg, InvalidPropertyFaultMsg {
        PropertySpec propertySpec = new PropertySpec();
        propertySpec.setAll(Boolean.FALSE);
        propertySpec.getPathSet().add(propertyString);
        propertySpec.setType(type);

        // Now create Object Spec
        ObjectSpec objectSpec = new ObjectSpec();
        objectSpec.setObj(ref);
        objectSpec.setSkip(Boolean.FALSE);
        objectSpec.getSelectSet().addAll(buildFullTraversal());
        // Create PropertyFilterSpec using the PropertySpec and ObjectPec
        // created above.
        PropertyFilterSpec propertyFilterSpec = new PropertyFilterSpec();
        propertyFilterSpec.getPropSet().add(propertySpec);
        propertyFilterSpec.getObjectSet().add(objectSpec);
        List<PropertyFilterSpec> listPfs = new ArrayList<PropertyFilterSpec>(1);
        listPfs.add(propertyFilterSpec);
        List<ObjectContent> oContList = retrievePropertiesAllObjects(listPfs);
        ObjectContent contentObj = oContList.get(0);
        List<DynamicProperty> objList = contentObj.getPropSet();
        return objList;
    }


    /*
* @return An array of SelectionSpec covering VM, Host, Resource pool,
* Cluster Compute Resource and Datastore.
*/
    List<SelectionSpec> buildFullTraversal() {
        // Terminal traversal specs

        // RP -> VM
        TraversalSpec rpToVm = new TraversalSpec();
        rpToVm.setName("rpToVm");
        rpToVm.setType("ResourcePool");
        rpToVm.setPath("vm");
        rpToVm.setSkip(Boolean.FALSE);

        // vApp -> VM
        TraversalSpec vAppToVM = new TraversalSpec();
        vAppToVM.setName("vAppToVM");
        vAppToVM.setType("VirtualApp");
        vAppToVM.setPath("vm");

        // HostSystem -> VM
        TraversalSpec hToVm = new TraversalSpec();
        hToVm.setType("HostSystem");
        hToVm.setPath("vm");
        hToVm.setName("hToVm");
        hToVm.getSelectSet().add(getSelectionSpec("visitFolders"));
        hToVm.setSkip(Boolean.FALSE);

        // DC -> DS
        TraversalSpec dcToDs = new TraversalSpec();
        dcToDs.setType("Datacenter");
        dcToDs.setPath("datastore");
        dcToDs.setName("dcToDs");
        dcToDs.setSkip(Boolean.FALSE);

        // Recurse through all ResourcePools
        TraversalSpec rpToRp = new TraversalSpec();
        rpToRp.setType("ResourcePool");
        rpToRp.setPath("resourcePool");
        rpToRp.setSkip(Boolean.FALSE);
        rpToRp.setName("rpToRp");
        rpToRp.getSelectSet().add(getSelectionSpec("rpToRp"));
        rpToRp.getSelectSet().add(getSelectionSpec("rpToVm"));

        TraversalSpec crToRp = new TraversalSpec();
        crToRp.setType("ComputeResource");
        crToRp.setPath("resourcePool");
        crToRp.setSkip(Boolean.FALSE);
        crToRp.setName("crToRp");
        crToRp.getSelectSet().add(getSelectionSpec("rpToRp"));
        crToRp.getSelectSet().add(getSelectionSpec("rpToVm"));

        TraversalSpec crToH = new TraversalSpec();
        crToH.setSkip(Boolean.FALSE);
        crToH.setType("ComputeResource");
        crToH.setPath("host");
        crToH.setName("crToH");

        TraversalSpec dcToHf = new TraversalSpec();
        dcToHf.setSkip(Boolean.FALSE);
        dcToHf.setType("Datacenter");
        dcToHf.setPath("hostFolder");
        dcToHf.setName("dcToHf");
        dcToHf.getSelectSet().add(getSelectionSpec("visitFolders"));

        TraversalSpec vAppToRp = new TraversalSpec();
        vAppToRp.setName("vAppToRp");
        vAppToRp.setType("VirtualApp");
        vAppToRp.setPath("resourcePool");
        vAppToRp.getSelectSet().add(getSelectionSpec("rpToRp"));

        TraversalSpec dcToVmf = new TraversalSpec();
        dcToVmf.setType("Datacenter");
        dcToVmf.setSkip(Boolean.FALSE);
        dcToVmf.setPath("vmFolder");
        dcToVmf.setName("dcToVmf");
        dcToVmf.getSelectSet().add(getSelectionSpec("visitFolders"));

        // For Folder -> Folder recursion
        TraversalSpec visitFolders = new TraversalSpec();
        visitFolders.setType("Folder");
        visitFolders.setPath("childEntity");
        visitFolders.setSkip(Boolean.FALSE);
        visitFolders.setName("visitFolders");
        List<SelectionSpec> sspecarrvf = new ArrayList<SelectionSpec>();
        sspecarrvf.add(getSelectionSpec("visitFolders"));
        sspecarrvf.add(getSelectionSpec("dcToVmf"));
        sspecarrvf.add(getSelectionSpec("dcToHf"));
        sspecarrvf.add(getSelectionSpec("dcToDs"));
        sspecarrvf.add(getSelectionSpec("crToRp"));
        sspecarrvf.add(getSelectionSpec("crToH"));
        sspecarrvf.add(getSelectionSpec("hToVm"));
        sspecarrvf.add(getSelectionSpec("rpToVm"));
        sspecarrvf.add(getSelectionSpec("rpToRp"));
        sspecarrvf.add(getSelectionSpec("vAppToRp"));
        sspecarrvf.add(getSelectionSpec("vAppToVM"));

        visitFolders.getSelectSet().addAll(sspecarrvf);

        List<SelectionSpec> resultspec = new ArrayList<SelectionSpec>();
        resultspec.add(visitFolders);
        resultspec.add(dcToVmf);
        resultspec.add(dcToHf);
        resultspec.add(dcToDs);
        resultspec.add(crToRp);
        resultspec.add(crToH);
        resultspec.add(hToVm);
        resultspec.add(rpToVm);
        resultspec.add(vAppToRp);
        resultspec.add(vAppToVM);
        resultspec.add(rpToRp);

        return resultspec;
    }


    SelectionSpec getSelectionSpec(String name) {
        SelectionSpec genericSpec = new SelectionSpec();
        genericSpec.setName(name);
        return genericSpec;
    }

    /**
     * Retrieves the MOREF of the host.
     *
     * @param hostName :
     * @return
     */
    ManagedObjectReference getHostByHostName(String hostName) throws RuntimeFaultFaultMsg, InvalidPropertyFaultMsg {
        ManagedObjectReference retVal = null;
        ManagedObjectReference rootFolder = connection.getServiceContent().getRootFolder();
        TraversalSpec tSpec = getHostSystemTraversalSpec();
        // Create Property Spec
        PropertySpec propertySpec = new PropertySpec();
        propertySpec.setAll(Boolean.FALSE);
        propertySpec.getPathSet().add("name");
        propertySpec.setType("HostSystem");

        // Now create Object Spec
        ObjectSpec objectSpec = new ObjectSpec();
        objectSpec.setObj(rootFolder);
        objectSpec.setSkip(Boolean.TRUE);
        objectSpec.getSelectSet().add(tSpec);

        // Create PropertyFilterSpec using the PropertySpec and ObjectPec
        // created above.
        PropertyFilterSpec propertyFilterSpec = new PropertyFilterSpec();
        propertyFilterSpec.getPropSet().add(propertySpec);
        propertyFilterSpec.getObjectSet().add(objectSpec);
        List<PropertyFilterSpec> listpfs =
                new ArrayList<PropertyFilterSpec>(1);
        listpfs.add(propertyFilterSpec);
        List<ObjectContent> listobjcont =
                retrievePropertiesAllObjects(listpfs);

        if (listobjcont != null) {
            for (ObjectContent oc : listobjcont) {
                ManagedObjectReference mr = oc.getObj();
                String hostnm = null;
                List<DynamicProperty> listDynamicProps = oc.getPropSet();
                DynamicProperty[] dps =
                        listDynamicProps
                                .toArray(new DynamicProperty[listDynamicProps.size()]);
                if (dps != null) {
                    for (DynamicProperty dp : dps) {
                        hostnm = (String) dp.getVal();
                    }
                }
                if (hostnm != null && hostnm.equals(hostName)) {
                    retVal = mr;
                    break;
                }
            }
        } else {
            System.out.println("The Object Content is Null");
        }
        if (retVal == null) {
            throw new RuntimeException("Host " + hostName + " not found.");
        }
        return retVal;
    }


    /**
     * Uses the new RetrievePropertiesEx method to emulate the now deprecated
     * RetrieveProperties method.
     *
     * @param listpfs
     * @return list of object content
     * @throws Exception
     */
    List<ObjectContent> retrievePropertiesAllObjects(
            List<PropertyFilterSpec> listpfs) throws RuntimeFaultFaultMsg, InvalidPropertyFaultMsg {
        RetrieveOptions propObjectRetrieveOpts = new RetrieveOptions();
        List<ObjectContent> listobjcontent = new ArrayList<ObjectContent>();

        RetrieveResult rslts =
                connection.getVimPort().retrievePropertiesEx(propCollectorRef, listpfs,
                        propObjectRetrieveOpts);
        if (rslts != null && rslts.getObjects() != null
                && !rslts.getObjects().isEmpty()) {
            listobjcontent.addAll(rslts.getObjects());
        }
        String token = null;
        if (rslts != null && rslts.getToken() != null) {
            token = rslts.getToken();
        }
        while (token != null && !token.isEmpty()) {
            rslts =
                    connection.getVimPort().continueRetrievePropertiesEx(propCollectorRef, token);
            token = null;
            if (rslts != null) {
                token = rslts.getToken();
                if (rslts.getObjects() != null && !rslts.getObjects().isEmpty()) {
                    listobjcontent.addAll(rslts.getObjects());
                }
            }
        }

        return listobjcontent;
    }


    /**
     * @return TraversalSpec specification to get to the HostSystem managed
     *         object.
     */
    TraversalSpec getHostSystemTraversalSpec() {
        // Create a traversal spec that starts from the 'root' objects
        // and traverses the inventory tree to get to the Host system.
        // Build the traversal specs bottoms up
        SelectionSpec ss = new SelectionSpec();
        ss.setName("VisitFolders");

        // Traversal to get to the host from ComputeResource
        TraversalSpec computeResourceToHostSystem = new TraversalSpec();
        computeResourceToHostSystem.setName("computeResourceToHostSystem");
        computeResourceToHostSystem.setType("ComputeResource");
        computeResourceToHostSystem.setPath("host");
        computeResourceToHostSystem.setSkip(false);
        computeResourceToHostSystem.getSelectSet().add(ss);

        // Traversal to get to the ComputeResource from hostFolder
        TraversalSpec hostFolderToComputeResource = new TraversalSpec();
        hostFolderToComputeResource.setName("hostFolderToComputeResource");
        hostFolderToComputeResource.setType("Folder");
        hostFolderToComputeResource.setPath("childEntity");
        hostFolderToComputeResource.setSkip(false);
        hostFolderToComputeResource.getSelectSet().add(ss);

        // Traversal to get to the hostFolder from DataCenter
        TraversalSpec dataCenterToHostFolder = new TraversalSpec();
        dataCenterToHostFolder.setName("DataCenterToHostFolder");
        dataCenterToHostFolder.setType("Datacenter");
        dataCenterToHostFolder.setPath("hostFolder");
        dataCenterToHostFolder.setSkip(false);
        dataCenterToHostFolder.getSelectSet().add(ss);

        //TraversalSpec to get to the DataCenter from rootFolder
        TraversalSpec traversalSpec = new TraversalSpec();
        traversalSpec.setName("VisitFolders");
        traversalSpec.setType("Folder");
        traversalSpec.setPath("childEntity");
        traversalSpec.setSkip(false);

        List<SelectionSpec> sSpecArr = new ArrayList<SelectionSpec>();
        sSpecArr.add(ss);
        sSpecArr.add(dataCenterToHostFolder);
        sSpecArr.add(hostFolderToComputeResource);
        sSpecArr.add(computeResourceToHostSystem);
        traversalSpec.getSelectSet().addAll(sSpecArr);
        return traversalSpec;
    }


}

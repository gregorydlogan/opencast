Config Options
==============

File System Based Asset Store
-----------------------------
Configure the file system based asset store in custom.properties.

- `org.opencast.episode.rootdir`  
   The path where the file system based asset store of the default implementation stores the assets. This key is optional.
- `org.opencast.storage.dir`  
  This is Opencast’s general config key to configure the base path of everything storage related. 
  If no storage directory is configured explicitely, the file system based asset store will use 
  `${org.opencast.storage.dir}/archive` as its base path.

Deployment
==========
The following bundles have to be added to the `system.properties`

- opencast-asset-manager-api
- opencast-asset-manager-impl
- opencast-asset-manager-storage-fs
- opencast-asset-manager-util
- opencast-asset-manager-workflowoperation

How can I use a different storage backend?
------------------------------------------
Replace the `opencast-asset-manager-storage-fs` bundle with another bundle that exports an implementation of the `AssetStore` interface.

How can I use a totally different AssetManager implementation?
--------------------------------------------------------------
Replace both `opencast-asset-manager-impl` and `opencast-asset-manager-storage-fs` bundles 
with a bundle that exports an implementation of the `opencast-asset-manager-api`.

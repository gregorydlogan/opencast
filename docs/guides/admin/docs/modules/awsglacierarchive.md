AWS Glacier Archive Configuration
=================================
This page documents the configuration for the AWS Glacier components in the Opencast module **matterhorn-archive-storage-aws**.  This configuration is only required on the admin node, and only if you are using Amazon Glacier as an archive repository.

Amazon User Configuration
-------------------------

Configuration of Amazon users is beyond the scope of this documentation, instead we suggest referring to [Amazon's documentation](http://docs.aws.amazon.com/IAM/latest/UserGuide/introduction.html).  You will, however, require an [Access Key ID and Secret Access Key](https://aws.amazon.com/developers/access-keys/).  The user to which this key belongs *requires* the *AmazonGlacierFullAccess* permission, which can be granted using [these instructions](http://docs.aws.amazon.com/IAM/latest/UserGuide/access_policies_inline-using.html).

**A [free Amazon account](https://aws.amazon.com/free/) will work for small scale testing, but be aware that Glacier archiving can cost you a lot of money very quickly.  Be aware of how much data and how many requests you are making, and be sure to [set alarms](http://docs.aws.amazon.com/awsaccountbilling/latest/aboutv2/free-tier-alarms.html) to notify you of cost overruns.**

Amazon Service Configuration
----------------------------

The development and testing it is generally safe to allow the Opencast AWS Glacier Archive service to create the Glacier vault for you.  It will create the vault per its configuration described below.

Opencast Service Configuration
------------------------------

The Opencast AWS S3 Archive service has four configuration keys which can be found in the `org.opencastproject.archive.aws.glacier.AwsGlacierArchiveElementStore.cfg` configuration file.

|Key name|Value|Example|
|org.opencastproject.archive.aws.glacier.region|The AWS region to set|us-west-2|
|org.opencastproject.archive.aws.glacier.vault|The Glacier vault name|example-org-vault|
|org.opencastproject.archive.aws.glacier.access.id|Your access ID|20 alphanumeric characters|
|org.opencastproject.archive.aws.glacier.secret.key|Your secret key|40 characters|

Using Glacier Archiving
------------------

There are two major methods to access Glacier archiving features: manually, and via a workflow.  Amazon Glacier archiving is not part of the default workflows and manual Glacier offload is disabled by default.  To enable manual Glacier offload you must edit the `ng-offload.xml` workflow configuration file and change `var glacierEnabled = false;` to `var glacierEnabled = true;`.  To manually offload a mediapackage to Glacier follow the directions [here](???)


To automatically offload a mediapackage to Glacier you must add the `move-to-remote` workflow operation to your workflow.  The operation documentation can be found [here](../workflowoperationhandlers/move-to-remote-woh.md).

Migrating to Glacier Archiving with Pre-Existing Data
---------------------------------------------------

Archiving to Glacier is a non-destructive operation in that it is safe to move archive files back and forth between local storage and Glacier.  To offload your local archive, select the workflow(s) and follow the manual offload steps described in the user documentation.

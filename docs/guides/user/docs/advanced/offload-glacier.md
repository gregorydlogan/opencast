[img_select_events]: media/img/select_events.png
[img_confirm_events]: media/img/confirm_events.png
[img_offload_task]: media/img/offload_task.png
[img_offload_glacier_1]: media/img/offload_glacier_1.png
[img_offload_glacier_2]: media/img/offload_glacier_2.png

# Offloading to Glacier

To manually offload a mediapackage to Glacier you select the events you wish to offload, and start a task

![img_select_events][]

## Step 1: Confirm the events

![img_confirm_events][]

This page confirms which event(s) you wish to offload

## Step 2: Select the task

![img_offload_task][]

Select the Offload task from the dropdown

## Step 3: Select the offload type

![img_offload_glacier_1][]

Select AWS Glacier in the list of offload targets

## Step 4: Confirm

![img_offload_glacier_2][]

Confirm that the correct values have been set

At this point a workflow will launch which will archive the event to AWS Glacier.  Accessing this mediapackage will be
transparent to you if it is required for further tasks, however be aware that restoring from Glacier can take many
hours.

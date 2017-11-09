angular.module('adminNg.controllers')
.controller('ServicesCtrl', ['$scope', 'Table', 'ServicesResource', 'ServiceResource', 'ResourcesFilterResource',
    function ($scope, Table, ServicesResource, ServiceResource, ResourcesFilterResource) {

        $scope.fullStats = false;
        $scope.fullcolumns = [{
                name:  'status',
                label: 'SYSTEMS.SERVICES.TABLE.STATUS'
            }, {
                name:  'name',
                label: 'SYSTEMS.SERVICES.TABLE.NAME'
            }, {
                name:  'hostname',
                label: 'SYSTEMS.SERVICES.TABLE.HOST_NAME'
            }, {
                name:  'completed',
                label: 'SYSTEMS.SERVICES.TABLE.COMPLETED'
            }, {
                name:  'running',
                label: 'SYSTEMS.SERVICES.TABLE.RUNNING'
            }, {
                name:  'queued',
                label: 'SYSTEMS.SERVICES.TABLE.QUEUED'
            }, {
                name:  'meanRunTime',
                label: 'SYSTEMS.SERVICES.TABLE.MEAN_RUN_TIME'
            }, {
                name:  'meanQueueTime',
                label: 'SYSTEMS.SERVICES.TABLE.MEAN_QUEUE_TIME'
            }, {
                template: 'modules/systems/partials/serviceActionsCell.html',
                label:    'SYSTEMS.SERVICES.TABLE.ACTION',
                dontSort: true
            }];
        $scope.columns = [{
                name:  'status',
                label: 'SYSTEMS.SERVICES.TABLE.STATUS'
            }, {
                name:  'name',
                label: 'SYSTEMS.SERVICES.TABLE.NAME'
            }, {
                name:  'hostname',
                label: 'SYSTEMS.SERVICES.TABLE.HOST_NAME'
            }, {
                name:  'completed',
                label: 'SYSTEMS.SERVICES.TABLE.COMPLETED'
            }, {
                name:  'running',
                label: 'SYSTEMS.SERVICES.TABLE.RUNNING'
            }, {
                name:  'queued',
                label: 'SYSTEMS.SERVICES.TABLE.QUEUED'
            }, {
                template: 'modules/systems/partials/serviceActionsCell.html',
                label:    'SYSTEMS.SERVICES.TABLE.ACTION',
                dontSort: true
            }];
        $scope.table = Table;
        $scope.reconfigTable = function() {
          if ($scope.fullStats) {
            tableColumns = $scope.fullcolumns;
          }
          $scope.table.configure({
              columns: tableColumns,
              caption:    'SYSTEMS.SERVICES.TABLE.CAPTION',
              resource:   'services',
              category:   'systems',
              queryparams: {'full': $scope.fullStats},
              apiService: ServicesResource
          });
        };
        $scope.reconfigTable();

        $scope.filters = ResourcesFilterResource.get({ resource: $scope.table.resource });

        $scope.table.sanitize = function (hostname, serviceType) {
            ServiceResource.sanitize({
                host: hostname,
                serviceType: serviceType
            }, function () {
                $scope.table.fetch();
            });
        };
    }
]);

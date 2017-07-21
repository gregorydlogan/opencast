/**
 * @ngdoc directive
 * @name ng.directive:adminNgEditableSingleSelect
 *
 * @description
 * Upon click on its label, this directive will display an <select> field
 * which will be transformed back into a label on blur.
 *
 * @element field
 * The "params" attribute contains an object with the attributes `id`,
 * `required` and `value`.
 * The "collection" attribute contains a hash of objects (or a promise thereof)
 * which maps values to their labels.
 * The "save" attribute is a reference to a save function used to persist
 * the value.
 *
 * @example
   <doc:example>
     <doc:source>
      <div admin-ng-editable-single-select params="params" save="save" collection="collection"></div>
     </doc:source>
   </doc:example>
 */
angular.module('adminNg.directives')
.directive('adminNgEditableSingleSelect', ['$timeout', function ($timeout) {
    return {
        restrict: 'A',
        templateUrl: 'shared/partials/editableSingleSelect.html',
        replace: true,
        scope: {
            params:     '=',
            collection: '=',
            save:       '='
        },
        link: function (scope, element) {
            scope.submit = function () {
                // Wait until the change of the value propagated to the parent's
                // metadata object.
                scope.submitTimer = $timeout(function () {
                    scope.save(scope.params.key);
                });
                scope.editMode = false;
            };

            scope.getLabel = function (key) {
                var label;

                angular.forEach(scope.collection, function (value, index) {
                    if (value === key) {
                        label = index;
                    }
                });

                return label;
            };

            scope.$on('$destroy', function () {
                $timeout.cancel(scope.submitTimer);
            });

            scope.enterEditMode = function () {
                // Store the original value for later comparision or undo
                if (!angular.isDefined(scope.original)) {
                    scope.original = scope.params.value;
                }
                scope.editMode = true;
                scope.focusTimer = $timeout(function () {
                  if ($('[chosen]')) {
                    element.find('select').trigger('chosen:activate');
                  }
                });
            };

            scope.leaveEditMode = function () {
                // does not work currently, as angular chose does not support ng-blur yet. But it does not break anything
                scope.editMode = false;
            };
       }
    };
}]);

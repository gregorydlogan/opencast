angular.module('adminNg.directives')
.directive('adminNgNotification', ['Notifications', '$timeout',
function (Notifications, $timeout) {
    return {
        restrict: 'A',
        templateUrl: 'shared/partials/notification.html',
        replace: true,
        scope: {
            id         : '@',
            type       : '@',
            message    : '@',
            parameters : '@',
            show       : '=',
            hidden     : '=',
            duration   : '@'
        },
        link: function (scope, element) {

        	if (angular.isNumber(parseInt(scope.duration)) && parseInt(scope.duration) !== -1) {
                // we fade out the notification if it is not -1 -> therefore -1 means 'stay forever'
                var fadeOutTimer = $timeout(function () {
                    element.fadeOut(function () {
                        Notifications.remove(scope.id, scope.$parent.context);
                    });
                }, scope.duration);

                 scope.$on('$destroy', function () {
                     $timeout.cancel(fadeOutTimer);
                 });
            }
       }
    };
}]);

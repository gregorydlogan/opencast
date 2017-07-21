angular.module('adminNg.directives')
.directive('adminNgSegments', ['PlayerAdapter', '$document', 'VideoService', '$timeout',
function (PlayerAdapter, $document, VideoService, $timeout) {
    return {
        templateUrl: 'shared/partials/segments.html',
        priority: 0,
        scope: {
            player: '=',
            video: '='
        },
        link: function (scope, element) {

            /**
            * Formats time stamps to HH:MM:SS.sss
            *
            * @param {Number} ms is the time in milliseconds,
            * @param {Boolean} showMilliseconds should the milliseconds be displayed
            * @return {String} Formatted time string
           */
            scope.formatMilliseconds = function (ms, showMilliseconds) {

               if (isNaN(ms)) {
                   return '';
               }

               var date = new Date(ms),
                   pad = function (number, padding) {
                       return (new Array(padding + 1).join('0') + number)
                           .slice(-padding);
                   };

               if (typeof showMilliseconds === 'undefined') {
                   showMilliseconds = true;
               }

               return pad(date.getUTCHours(), 2) + ':' +
                   pad(date.getUTCMinutes(), 2) + ':' +
                   pad(date.getUTCSeconds(), 2) +
                   (showMilliseconds ? '.' + pad(date.getUTCMilliseconds(), 3) : '');
            };

            /**
             * Converts a string with a human readable time to ms
             *
             * @param {type} time in the format HH:MM:SS.sss
             * @returns {Number} time in ms
             */
            scope.parseTime = function (time) {
              if ( time !== undefined && time.length === 12) {
                var hours = parseInt(time.substring(0,2)),
                    minutes = parseInt(time.substring(3,5)),
                    seconds = parseInt(time.substring(6,8)),
                    millis = parseInt(time.substring(9));

                return millis + (seconds * 1000) + (minutes * 60000) + (hours * 36000000);
              }
            };

            /**
             * Returns an object that describes the css classes for a given segment.
             *
             * @param {Object} segment object
             * @return {Object} object with {class}: {boolean} values for CSS classes.
             */
            scope.getSegmentClass = function (segment) {
                return { deleted: segment.deleted, selected: segment.selected};
            };

            /**
             * Removes the given segment.
             *
             * The previous or, failing that, the next segment will take up
             * the space of the given segment.
             *
             * @param {Event} event that triggered the merge action
             * @param {Object} segment Segment object
             */
            scope.mergeSegment = function (event, segment) {
                if (event) {
                    event.preventDefault();
                    event.stopPropagation();
                }

                var index = scope.video.segments.indexOf(segment);

                if (scope.video.segments[index - 1]) {
                    scope.video.segments[index - 1].end = segment.end;
                    scope.video.segments.splice(index, 1);
                } else if (scope.video.segments[index + 1]) {
                    scope.video.segments[index + 1].start = segment.start;
                    scope.video.segments.splice(index, 1);
                }
              scope.$root.$broadcast("segmentTimesUpdated");
            };

            /**
             * Toggle the deleted flag for a segment. Indicating if it should be used or not.
             *
             * @param {Event} event for checkbox link - stop the propogation
             * @param {Object} segment object on which the deleted variable will change
             */
            scope.toggleSegment = function (event, segment) {
                if (event) {
                    event.preventDefault();
                    event.stopPropagation();
                }

                segment.deleted = !segment.deleted;
            };

            /**
             * Sets the position marker to the start of the given segment.
             *
             * @param {Event} event details
             * @param {Object} segment Segment object
             */
            scope.skipToSegment = function (event, segment) {
                event.preventDefault();

                if (!segment.selected) {
                    scope.player.adapter.setCurrentTime(segment.start / 1000);
                }
            };

            /**
             * Sets / Updates the human readable start and end times of the segments.
             */
            scope.setHumanReadableTimes = function () {
              angular.forEach(scope.video.segments, function(segment, key) {
                segment.startTime = scope.formatMilliseconds(segment.start);
                segment.endTime = scope.formatMilliseconds(segment.end);
              });
            };

            /*
             * Make sure that times are updates if needed.
             */
            scope.$root.$on("segmentTimesUpdated", function () {
              scope.setHumanReadableTimes();
            });

            /**
             * Checks if a time is within the valid boundaries
             * @param {type} time time to check
             * @returns {Boolean} true if time is > 0 and <video duration
             */
            scope.timeValid = function (time) {
              if (time >= 0 && time <= scope.video.duration) {
                return true;
              } else {
                return false;
              }
            };

            /**
             * Set a new Start time of a segment, other segments are changed or deleted accordingly
             * @param {type} segment that should change
             */

            scope.updateStartTime = function (segment) {
              var newTime = scope.parseTime(segment.startTime);
              if (newTime && newTime !== segment.start) {
                if (newTime > segment.end || ! scope.timeValid(newTime)) {
                  segment.startTime = scope.formatMilliseconds(segment.start);
                } else {
                  var previousSegment = scope.getPreviousSegment(segment);
                  segment.start = newTime;
                  while (previousSegment &&previousSegment.start > newTime) {
                    scope.removeSegment(previousSegment);
                    previousSegment = scope.getPreviousSegment(segment);
                  }
                  if (previousSegment) {
                    previousSegment.end = newTime;
                    scope.$root.$broadcast("segmentTimesUpdated");
                  }
                }
              }
            };

            /**
             * Sets a new end time of a segment, other segments are changed or deleted accordingly
             * @param {type} segment that should change
             */
            scope.updateEndTime = function (segment) {
              var newTime = scope.parseTime(segment.endTime);
              if (newTime && newTime !== segment.end) {
                if (newTime < segment.start || ! scope.timeValid(newTime)) {
                  segment.endTime = scope.formatMilliseconds(segment.end);
                } else {
                  var nextSegment = scope.getNextSegment(segment);
                  segment.end = newTime;
                  while (nextSegment && nextSegment.end < newTime) {
                    scope.removeSegment(nextSegment);
                    nextSegment = scope.getNextSegment(segment);
                  }
                  if (nextSegment) {
                    nextSegment.start = newTime;
                    scope.$root.$broadcast("segmentTimesUpdated");
                  }
                }
              }
            };

            /**
             * Deletes a segment from the segment list. Times of other segments are not changed!
             * @param {type} segment that should be deleted
             */
            scope.removeSegment = function (segment) {
              if (segment) {
                var index = scope.video.segments.indexOf(segment);
                scope.video.segments.splice(index, 1);
              }
            };

            /**
             * Gets the segment previous to the provided segment.
             * @param {type} currentSegment the reference segment
             * @returns {unresolved} the previous segment or "undefinded" if current segment is the first
             */
            scope.getPreviousSegment = function (currentSegment) {
              var index = scope.video.segments.indexOf(currentSegment);
              if (index > 0)
                return scope.video.segments[index - 1];
            };

            /**
             * Gets the next segment to the provided segment
             * @param {type} currentSegment the reference segment
             * @returns {unresolved} the next segment or "undefined" if the current segment is the last.
             */
            scope.getNextSegment = function (currentSegment) {
              var index = scope.video.segments.indexOf(currentSegment);
              if (index < (scope.video.segments.length - 1))
                return scope.video.segments[index + 1];
            };

            scope.setHumanReadableTimes();

            $timeout(function () {
              scope.$root.$broadcast("segmentTimesUpdated");
            }, 300);
        }
    };
}]);
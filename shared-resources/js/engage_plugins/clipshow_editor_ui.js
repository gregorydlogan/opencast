/**
 *  Copyright 2009-2011 The Regents of the University of California
 *  Licensed under the Educational Community License, Version 2.0
 *  (the "License"); you may not use this file except in compliance
 *  with the License. You may obtain a copy of the License at
 *
 *  http://www.osedu.org/licenses/ECL-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an "AS IS"
 *  BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 *  or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 *
 */
 
var Opencast = Opencast || {};
Opencast.clipshow = Opencast.clipshow || {};

/**
 * @namespace the global Opencast namespace segments_ui_Plugin
 */
Opencast.clipshow.editor = (function ()
{

    var clipshowEditorDisplayed = false;

    var minClipSize = 1;

    var templateComponent = '{for c in clips}' +
                                '<div '+
                                      'class="clipshow-editor-component segment-holder ui-widget ui-widget-content" ' +
                                      'id="${c.id}" ' +
                                      'style="position: absolute; left: ${c.start * pps}px; width: ${(c.stop - c.start) * pps}px;">' +
                                      '<p>${c.index}</p>' +
                                 '</div>' +
                             '{/for}';

    // The number of clips created so far
    var counter = 0;

    // The current positions (in seconds) of the start and stop points for each segment
    var currentPositions = {};

    // The Elements to put the div into
    var elementClipshowEditor;
    
    /**
     * @memberOf Opencast.clipshow.editor
     * @description Add As Plug-in
     * @param elemClipshowEditor The element to display the clipshow editor in
     */
    function addAsPlugin(elemClipshowEditor) {
        elementClipshowEditor = elemClipshowEditor;
        if (currentPositions.clips == undefined) {
          currentPositions.clips = {};
        }
    }

    function show() 
    {
      Opencast.Player.addEvent("CLIPSHOW_EDITOR_SHOW");
      Opencast.clipshow.editor.addAsPlugin($('#clipshoweditor'));
      $("#oc_label-save").show();
      $("#oc_label-add").show();
      $("#oc_button-clipshowSave").show();
      $("#oc_button-clipshowEditorAdd").show();
      $('#clipshoweditor').show();
      if (Opencast.clipshow.ui.isShown())
      {
	    $('#clipshoweditor').css({'top': '54px'});
        $('#analytics-and-annotations').height("82px");
      }
      else
      {
      	$('#clipshoweditor').css({'top': '26px'});
        $('#analytics-and-annotations').height("55px");
      }
      $("#oc_video-view").css({"height": "35px", "margin-top": "0px"});
      clipshowEditorDisplayed = true;
    }

    function hide() 
    {
      Opencast.Player.addEvent("CLIPSHOW_EDITOR_HIDE");
      $("#oc_label-add").hide();
      $("#oc_label-save").hide();
      $("#oc_button-clipshowSave").hide();
      $("#oc_button-clipshowEditorAdd").hide();
      $('#clipshoweditor').hide();
      if (Opencast.clipshow.ui.isShown())
      {
        $('#analytics-and-annotations').height("55px");
        $("#oc_video-view").css({"height": "25px"});
      }
      else
      {
        $('#analytics-and-annotations').css({'height': ''});
        $("#oc_video-view").css({"height": ""});
      }
      $("#oc_video-view").css({"margin-top": ""});
      clipshowEditorDisplayed = false;
    }
  
    function updateResizePosition(event, ui) {
      var start = ui.position.left;
      var stop = ui.size.width + start;
      var pps = Opencast.Player.getPixelsPerSecond();
      Opencast.Player.doSeek(Math.round(stop / pps));
      Opencast.Player.addEvent("RESIZE_CLIP_" + ui.helper.context.id + "_" + start + "_" + stop);
      currentPositions["clips"][ui.helper.context.id]["start"] = Math.round(start / pps);
      currentPositions["clips"][ui.helper.context.id]["stop"] = Math.round(stop / pps);
      redraw();
      return true;
    }

    function updateDragPosition(event, ui) {
      var start = ui.helper.position().left;
      var stop = ui.helper.width() + start;
      var pps = Opencast.Player.getPixelsPerSecond();
      Opencast.Player.doSeek(Math.round(start / pps));
      Opencast.Player.addEvent("MOVE_CLIP_" + ui.helper.context.id + "_" + start + "_" + stop);
      currentPositions["clips"][ui.helper.context.id]["start"] = Math.round(start / pps);
      currentPositions["clips"][ui.helper.context.id]["stop"] = Math.round(stop / pps);
      redraw();
      return true;
    }


    /**
     * @memberOf Opencast.clipshow.ui
     * @description Processes the Data and puts it into the Element
     */
    function createClipshowEditorElement() {
        var id = "edit-clip" + counter;
        currentPositions["clips"][id] = { "id": id, "index": counter, "stop": Math.round(Opencast.Player.getCurrentPosition()) };
        if (Math.round(currentPositions["clips"][id]["stop"] - 60 < 0))
        {
          currentPositions["clips"][id]["start"] = 0;
        }
        else
        {
          currentPositions["clips"][id]["start"] = currentPositions["clips"][id]["stop"] - 60;
        }
        counter++;
        Opencast.Player.addEvent("CREATE_CLIP_" + counter + "_" + currentPositions["clips"][id]["start"] + "_" + currentPositions["clips"][id]["stop"]);
        redraw();
    }

    function resize(event, ui) {
      Opencast.Player.doPause();
      var seekTime = -1;
      if (ui.originalPosition.left != ui.position.left) {
        seekTime = ui.position.left / Opencast.Player.getPixelsPerSecond();
      } else if (ui.originalSize.width != ui.size.width) {
        seekTime = (ui.position.left + ui.size.width) / Opencast.Player.getPixelsPerSecond();
      }
      return true;
    }

    function startResize(element) {
    	for (var i = 0; i < counter; i++)
        {
          $("#edit-clip" + i).draggable({disabled: true}).sortable({disabled: true});
        }
    }

    function redraw() 
    {
        var pps = Opencast.Player.getPixelsPerSecond();
        currentPositions["pps"] = pps;
        index = 0;
        var processedClipshowData = templateComponent.process(currentPositions);
        elementClipshowEditor.html(processedClipshowData);
        var clipSize = Math.round(minClipSize * pps);
        for (var i = 0; i < counter; i++)
        {
          $("#edit-clip" + i).resizable({handles: 'e', containment: elementClipshowEditor, grid: [ clipSize, clipSize ], minwidth: clipSize, start: startResize, stop: updateResizePosition, resize: resize});
          $("#edit-clip" + i).draggable({containment: elementClipshowEditor, grid: [ clipSize, clipSize ], axis: 'x', stop: updateDragPosition});
          $("#edit-clip" + i).sortable({revert: true});
        }
  	    elementClipshowEditor.disableSelection();
    }

    function saveDialog() {
	  Opencast.Player.addEvent("SAVE_DIALOG");
      if (counter > 0) {
        var playbackPositions = [];
        $("#oc_clipshow-dialog").dialog("open");
				currentPositions["pps"] = $('#oc-clipshow-ordering-source').width() / Opencast.Player.getDuration();
			  index = 0;
			  var processedData = templateComponent.process(currentPositions);
			  $("#oc-clipshow-ordering-source").html(processedData);

		    $("#oc-clipshow-ordering-dest").sortable({
		      stop: function(event, ui) {
		        //New items
		        if (!ui.item.data('tag')) {
		          ui.item.data('tag', true);
		          ui.item.children()[0].innerHTML = "Clip " + ui.item.attr("id").substring("edit-clip".length);
		        } else { //Removing old items
		          if (ui.position.left + ui.item.width() < $(this).position().left || 
	              ui.position.left > $(this).position().left + $(this).width() ||
	              ui.position.top + ui.item.height() < $(this).position().top ||
	              ui.position.top > $(this).position().top + $(this).height()) {
	              ui.item.remove();
	            }
	          }
		      },
		      receive: function(event, ui) {
		        var children = $("#oc-clipshow-ordering-dest > div.clipshow-editor-component");
		        var width = Math.min(1/children.length * 0.90 * $("#oc-clipshow-ordering-dest").width(), 120);
		        $.each(children, function() {
		          $(this).attr("style", "width: " + width + "px; margin-right: 2px; opacity: 1.0;");
		          //If it's a new item, copy the id so that things work later
		          if ('undefined' == typeof $(this).attr("id")) {
  		          $(this).attr("id", ui.item.attr("id"));
  		        }
		        });
		      }
		    });

        $.each($("#oc-clipshow-ordering-source > div.clipshow-editor-component"), function(index) {
          $(this).position({
            of: $("#oc-clipshow-ordering-source"),
            my: "left top",
            at: "left top",
            offset: ($(this).position().left + 2) + " 1",
            collision: "none"
          })
			  }).draggable({
          revert: "true",
          containment: "#oc-clipshow-ordering-container",
          connectToSortable: "#oc-clipshow-ordering-dest",
          helper: "clone",
          cursor: "move"
        });

  	    $("#oc-clipshow-ordering-source").disableSelection();
  	    $("#oc-clipshow-ordering-dest").disableSelection();

        $('#oc-input-series-name-select').autocomplete({
          source: function(request, response) {
            $.ajax({
              url: '../../clipshow/series/list',
              dataType: 'json',
              type: 'GET',
              success: function(data) {
                var series_list = [];
                data = data.wrapper;
                $.each(data, function(){
                  series_list.push({
                    value: this.title,
                    id: this.id
                  });
                });
                response(series_list);
              }, 
              error: function() {
                ocUtils.log('could not retrieve clipshow series data');
              }
            });
          },
          select: function(event, ui){
            $('#oc-input-series-id').val(ui.item.id);
          },
          search: function(){
            $('#oc-input-series-id').val('');
          }
        });
      }
    }

    function saveClipshow(title, series, seriesName, tags, allowedUsers) {
      var clips = [];
      $.each($("#oc-clipshow-ordering-dest").sortable('toArray'), function(index, value) { 
        clips.push(currentPositions.clips[value]);
      });

      $.ajax(
      {
          type: "POST",
          url: "../../clipshow/create",
          data: JSON.stringify({title: title, mediapackageId: Opencast.Player.getMediaPackageId(), mediapackageTitle: $("#oc_title-1").text(), clips: clips, series: series, newSeriesName: seriesName, tags: tags, allowedUsers: allowedUsers}),
          contentType: 'application/json; charset=utf-8',
          dataType: 'json',
          success: function (json)
          {
              Opencast.clipshow.core.refreshClipshowList();
              Opencast.Player.addEvent("SAVE_CLIPSHOW_" + title);
          },
          error: function (a, b, c)
          {
              // Some error while adding the clipshow
          }
      });
    }

    function isShown() {
      return clipshowEditorDisplayed;
    }

    return {
        addAsPlugin: addAsPlugin,
        hide: hide,
        show: show,
        redraw: redraw,
        createClipshowEditorElement: createClipshowEditorElement,
        saveDialog: saveDialog,
        saveClipshow: saveClipshow,
        isShown: isShown
    };
}());

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
Opencast.clipshow.ui = (function ()
{

    var clipshowDisplayed = false;

    var clipshowDropdownRefreshInterval;

    // The Templates to process
    var templateClipshow = '{for c in clips}' +
                                '<div '+
                                      'class="segment-holder ui-widget ui-widget-content clipshow-component" ' +
                                      'id="clip${c.id}" ' +
                                      'style="width: ${(c.stop - c.start) * pps}px; text-align: center;"' +
																			'onclick="Opencast.Player.doSeekToClip(${c.id});">' +
                                      '<p>&nbsp;</p>' +
                                 '</div>' +
                             '{/for}';

    // The Elements to put the div into
    var elementClipshow;
    
    // The data to display
    var clipshow_ui_data = false;
        
    // Precessed Data
    var processedClipshowdata;

    //The clipshow id
    var clipshowId;

    function show()
     {
      Opencast.Player.addEvent("CLIPSHOW_SHOW");
      $('#clipshowtable').show();
      $("#oc_label-select").show();
      $("#oc_select-clipshow").show(0, updateClipshowDropdown);
      $("#oc_checkbox-clipshow").attr("checked", true);

	  clipshowDisplayed = true;
      Opencast.clipshow.ui.addAsPlugin($('#clipshowtable'));
      if (Opencast.clipshow.editor.isShown())
      {
	    $('#clipshoweditor').css({'top': '54px'});
        $('#analytics-and-annotations').height("82px");
      }
      else
      {
        $('#analytics-and-annotations').height("55px");
        $("#oc_video-view").css({"height": "25px"});
      }
      $('#clipshowtable').css({'top': '26px'});
      $("#oc_video-view").css({"margin-top": "0px"});
    }

    function hide() 
    {
      Opencast.Player.addEvent("CLIPSHOW_HIDE");
	  Opencast.clipshow.core.switchToClipshow(false);
      $('#clipshowtable').hide();
      $("#oc_label-select").hide();
      $("#oc_select-clipshow").hide();
      $("#oc_votes").hide();
      $("#oc_clipshow-delete").hide();
      $("#oc_checkbox-clipshow").attr("checked", false);

      if (Opencast.clipshow.editor.isShown())
      {
        $('#analytics-and-annotations').height("55px");
        $('#clipshoweditor').css({'top': '26px'});
      }
      else
      {
        $('#analytics-and-annotations').css({'height': ''});
        $("#oc_video-view").css({"margin-top": "", "height": ""});
      }
      clearInterval(clipshowDropdownRefreshInterval);
      //Opencast.Player.setClips([]);
      setClipshowData(false);
      if (Opencast.clipshow.series.core != undefined) {
        Opencast.clipshow.series.core.stopSeries();
      }
      clipshowDisplayed = false;
      //Clear the hidden display as well
      draw();
    }

    function updateClipshowDropdown() {
      var clipshowList = Opencast.clipshow.core.getClipshowList();
      if (clipshowList == undefined) {
          setTimeout("Opencast.clipshow.ui.updateClipshowDropdown()", 250);
          return;
      }
      var selector = $("#oc_select-clipshow");
      selector.empty().append($("<option />").val("x").text("Select Clipshow"));
      for (var i = 0; i < clipshowList.length; i++) {
        selector.append($("<option />").val(clipshowList[i].id).text(clipshowList[i].title + " by " + clipshowList[i].author));
      }
      $("#oc_select-clipshow").val(Opencast.clipshow.core.getCurrentClipshow());
    }

	  function setClipshowData(clipshow) {
		  clipshow_ui_data = clipshow;
	  }

    function getClipshow(id) {
      //TODO:  Ensure that we're being passed an id rather than garbage
      if (id == 'x') {
        return;
      }

      if (!isShown()) {
        show();
      }

      Opencast.clipshow.core.setCurrentClipshow(id);
      $("#oc_select-clipshow").val(Opencast.clipshow.core.getCurrentClipshow());

      $.ajax({
            type: "GET",
            url: "../../clipshow/get?clipshowId=" + id  + "&mediapackageId=" + Opencast.Player.getMediaPackageId(),
            dataType: 'json',
            success: function(json) {
              
              var clipshowSource = json.clipshow;
              var clips = json.clipshow.clips;

              if (clips.length == 0) {
                return;
              }

							Opencast.Player.doPause();

              $("#oc_btn-vote-funny").removeAttr("checked");
              $("#oc_btn-vote-good").removeAttr("checked");
              $("#oc_btn-vote-dislike").removeAttr("checked");
              $("#oc_votes").buttonset("refresh");

              if (clipshowSource.author.id != Opencast.clipshow.core.getUserId()) {
        	    $("#oc_clipshow-delete").hide();
                $("#oc_votes").show();
                $.ajax({
                  type: "GET",
                  url: "../../clipshow/vote/mine",
                  data: 'clipshowId=' + id,
                  dataType: 'json',
                  success: function(json) {
                    //{"properties-response":{"properties":{"item":[{"key":"GOOD","value":0},{"key":"DISLIKE","value":0},{"key":"FUNNY","value":1}]}}}
                    var types = json["properties-response"].properties.item;
                    for (var i = 0; i < types.length; i++) {
                      if (types[i].key == "GOOD" && types[i].value == 1) {
                        $("#oc_btn-vote-good").attr("checked", true);
                      } else if (types[i].key == "FUNNY" && types[i].value == 1) {
                        $("#oc_btn-vote-funny").attr("checked", true);
                      } else if (types[i].key == "DISLIKE" && types[i].value == 1) {
                        $("#oc_btn-vote-dislike").attr("checked", true);
                        $("#oc_btn-vote-funny").removeAttr("checked");
                        $("#oc_btn-vote-good").removeAttr("checked");
                      }
                    }
                    $("#oc_votes").buttonset("refresh");
                  }
                });
              } else {
                $("#oc_votes").hide();
                //$("#oc_clipshow-delete").show();
              }

              //Ensure the clipshow is an array, even if there's only one clip
              if (!$.isArray(clips)) {
                clips = [clips];
              }

              Opencast.Player.setClips(clips);
              setClipshowData({clips: clips});
              Opencast.clipshow.ui.draw();
              //NB:  Seeking behaviour at start of clipshow is currently broken, appears to be a race condition
              //Opencast.Player.doPlay();
            }
          });
    }

    /**
     * @memberOf Opencast.clipshow.ui
     * @description Add As Plug-in
     * @param elemClipshow The element to display the clipshow in
     * @param withClipshow boolean True to display the clipshow, false otherwise
     */
    function addAsPlugin(elemClipshow) {
        elementClipshow = elemClipshow;

        draw();
    }

    /**
     * @memberOf Opencast.clipshow.ui
     * @description Processes the Data and puts it into the Element
     * @param withClipshow true if process with Clips, false if without Clips
     */
    function draw() {
        // Process Element Segments 1
        if ((elementClipshow !== undefined) && isShown() && (clipshow_ui_data !== false) && (clipshow_ui_data.clips.length > 0)) {
            clipshow_ui_data.pps = Opencast.Player.getPixelsPerSecond();
            processedClipshowData = templateClipshow.process(clipshow_ui_data);
            elementClipshow.html(processedClipshowData);
						for (var i = 0; i < clipshow_ui_data.clips.length; i++) {
							var clip = clipshow_ui_data.clips[i];
							var clipId = "#clip" + clip.id;
							var offset = "" + clip.start * clipshow_ui_data.pps + " 0"
							$(clipId).position({
								of: $("#clipshowtable"),
								my: "left top",
								at: "left top",
								offset: offset,
								collision: "none none"
							});
						}
            $.log("Clipshow UI Plugin: Clipshow initialized");
        } else if (elementClipshow !== undefined) {
          elementClipshow.html("");
          $.log("Cleared clipshow display");
        }
    }

    function isShown() {
      return clipshowDisplayed;
    }

    return {
        addAsPlugin: addAsPlugin,
        hide: hide,
        show: show,
        updateClipshowDropdown: updateClipshowDropdown,
        getClipshow: getClipshow,
        draw: draw,
        isShown: isShown
    };
}());

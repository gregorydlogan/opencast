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
 * @namespace the global Opencast namespace clipshow_ui
 */
Opencast.clipshow.core = (function ()
{
    var dateIn;
    var resizeEndTimeoutRunning = false,
        waitForMove = 150;
    var clipshowList;
    var currentClipshow = false,
        currentSeries = false;
    // User name and id
    var userCredentials;


    /**
     * @memberOf Opencast.segments_ui
     * @description Initializes the segments ui view
     */
    function initialize()
    {
        if (Opencast.Player.getMediaPackageId() == undefined) {
          setTimeout("Opencast.clipshow.core.initialize()", 250);
          return;
        }
        $("#oc_button-clipshowEditorAdd").button();
        $("#oc_button-clipshowEditorAdd").click(function ()
        {
            Opencast.clipshow.editor.createClipshowEditorElement();
        });

        $("#oc_button-clipshowSave").button();
        $("#oc_button-clipshowSave").click(function ()
        {
          Opencast.clipshow.editor.saveDialog();
        });
        $("#oc_select-clipshow").change(function() {
          if (getCurrentSeries() != false) {
        	  $( "#oc_clipshow-series-confirm-dialog" ).dialog({
      			resizable: false,
      			height:150,
      			modal: true,
      			buttons: {
      				"Stop series playback": function() {
      					if (Opencast.clipshow.series.core.isShown()) {
        					Opencast.clipshow.series.core.stopSeries();
        		            Opencast.clipshow.series.core.switchToSeries(false);
    		            }
      		            var id = $("#oc_select-clipshow option:selected").val();
      		            Opencast.clipshow.core.switchToClipshow(id);
      		            $( this ).dialog( "close" );
      				},
      				Cancel: function() {
      					$('#oc_select-clipshow').val(getCurrentClipshow());
      					$( this ).dialog( "close" );
      				}
      			}
      		});
          } else {
            var id = $("#oc_select-clipshow option:selected").val();
            Opencast.clipshow.core.switchToClipshow(id);
          }
        });
        $("#oc_votes").buttonset();
        $(".clipshow_votes").click(function() {
          if (($(this).attr("id") == 'oc_btn-vote-funny' || $(this).attr("id") == 'oc_btn-vote-good') && $(this).attr("checked")) {
            $("#oc_btn-vote-dislike").removeAttr("checked");
          } else if (($(this).attr("id") == 'oc_btn-vote-dislike') && $(this).attr("checked")) {
            $("#oc_btn-vote-funny").removeAttr("checked");
            $("#oc_btn-vote-good").removeAttr("checked");
          }

          if ($("#oc_btn-vote-dislike").attr("checked")) {
            postVotes("DISLIKE");
          } else if ($("#oc_btn-vote-funny").attr("checked") && $("#oc_btn-vote-good").attr("checked")) {
            postVotes("FUNNY,GOOD");
          } else if ($("#oc_btn-vote-funny").attr("checked")) {
            postVotes("FUNNY");
          } else if ($("#oc_btn-vote-good").attr("checked")) {
            postVotes("GOOD");
          } else {
            postVotes("NEUTRAL");
          }
          $("#oc_votes").buttonset("refresh");
        });
        $("#oc_clipshow-delete").button({text: "Delete Clipshow"}).click(function() {
	      $.ajax({
            type: "POST",
            url: "../../clipshow/delete", 
    		data: 'clipshowId=' + Opencast.clipshow.core.getCurrentClipshow(),
            dataType: 'json',
            success: function(json) {
            	//TODO:
            }
          });
        });

        updateUserCredentials();
       
        refreshClipshowList();
        window.setInterval(function event() {
            Opencast.clipshow.core.refreshClipshowList();
        }, 5 * 60 * 1000); //5 minutes
        initResizeEnd();
        Opencast.clipshow.ui.hide();
        Opencast.clipshow.editor.hide();
        //var reg = Opencast.Plugin_Controller.registerPlugin(Opencast.clipshow.editor);
	      //$.log("Opencast.clipshow.editor registered: " + reg);
	      //reg =  Opencast.Plugin_Controller.registerPlugin(Opencast.clipshow.ui);
	      //$.log("Opencast.clipshow.ui registered: " + reg);
        reg = Opencast.Plugin_Controller.registerPlugin(Opencast.clipshow.series.core);
        $.log("Opencast.clipshow.series.core registered: " + reg);
        reg = Opencast.Plugin_Controller.registerPlugin(Opencast.clipshow.user.core);
        $.log("Opencast.clipshow.user.core registered: " + reg);

        var currentClipshowId = $.getURLParameter('clipshowId');
        if (currentClipshowId && !isNaN(currentClipshowId)) {
    	  Opencast.Player.addEvent("URL_CLIPSHOW_" + currentClipshowId);
          switchToClipshow(currentClipshowId);
        }

        var currentSeriesId = $.getURLParameter('clipshowSeriesId');
        if (currentSeriesId && !isNaN(currentSeriesId)) {
          Opencast.Player.addEvent("URL_SERIES_" + currentSeriesId);
          switchToSeries(currentSeriesId);
          $("#oc_ui_tabs").tabs("select", "#oc_clipshow-series");
        }

        var statsEnabled = $.getURLParameter('statsEnabled');
        if (statsEnabled == "true") {
          Opencast.Analytics.show();
          $("#oc_ui_tabs").tabs("select", "#oc_comments");
        }

        if (getCurrentClipshow()) {
          $('#oc_checkbox-annotation-comment').attr("checked", true);
          Opencast.Annotation_Comment.show();
        }

        /*$.ajax({
            type: 'GET',
            url: "../../clipshow/consented",
            dataType: 'text',
            success: function (text)
            {
              if (text === 'false') {
                var consent = confirm("This is a research system, do you consent?");
                if (consent) {
                  $.ajax({
                    type: 'GET',
                    url: "../../clipshow/giveConsent",
                    dataType: 'text'
                  });
                }
              }
            }
          });*/
        if (!(currentSeriesId && !isNaN(currentSeriesId))) {
          $("#oc_ui_tabs").tabs("select", "#oc_clipshow-user");
        }

    }

    function updateUserCredentials() {
        $.ajax(
        {
            type: "GET",
            url: "../../clipshow/user/getName",
            dataType: 'json',
            success: function (json)
            {
              Opencast.clipshow.core.setUserCredentials(json);
            },
            error: function (a, b, c)
            {

            }
        });    
    }

    function setUserCredentials(json) {
      userCredentials = json;
    }

    function getUserId() {
      if (userCredentials) {
        return userCredentials.id;
      } else {
        return userCredentials;
      }
    }

    function getUsername() {
      if (userCredentials) {
        return userCredentials.displayName;
      } else {
        return userCredentials;
      }
    }

    function refreshClipshowList() {
      $.ajax({
        type: "GET",
        url: "../../clipshow/list?mediapackageId=" + Opencast.Player.getMediaPackageId(),
        dataType: 'json',
        success: function(json) {
          if ('undefined' != typeof json.wrapper.data) {
            if ($.isArray(json.wrapper.data)) {
              Opencast.clipshow.core.setClipshowList(json.wrapper.data);
            } else {
              Opencast.clipshow.core.setClipshowList([json.wrapper.data]);
            }
          } else {
            Opencast.clipshow.core.setClipshowList([]);
          }
          Opencast.clipshow.ui.updateClipshowDropdown();
          //TODO:  If you enable this then the tab keeps clearing things.  Need to render the tab by parts...
          /*if (Opencast.clipshow.series.core.isShown()) {
        	  Opencast.clipshow.series.core.updateClipshowData();
          }*/
        }
      });
    }

    function setClipshowList(json) {
      clipshowList = json;
    }

    function getClipshowList() {
      return clipshowList;
    }

    function postVotes(votes) {
      Opencast.Player.addEvent("VOTE_" + votes);
      $.ajax({
        type: "POST",
        url: "../../clipshow/vote/add",
        data: 'clipshowId=' + Opencast.clipshow.core.getCurrentClipshow() + '&types=' + votes,
        dataType: 'json',
        failure: function(json) {
          //TODO:  undo votes?
        }
      });
    }

    function switchToClipshow(clipshowId)
    {
      Opencast.Player.addEvent("SWITCH_CLIPSHOW_" + clipshowId);
      if (!Opencast.clipshow.ui.isShown()) {
        Opencast.clipshow.ui.show();
        $("#oc_checkbox-clipshow").attr("checked", true);
      }

      setCurrentClipshow(clipshowId);

      if (clipshowId != false) {
        Opencast.clipshow.ui.getClipshow(clipshowId);
      }

      //This seems silly, but it refreshes the annotation data!
      if (Opencast.Annotation_Comment.isShown()) {
        Opencast.Annotation_Comment.hide();
        Opencast.Annotation_Comment.show();
      }
      if (Opencast.Annotation_Comment_List.isShown()) {
        Opencast.Annotation_Comment_List.hide();
        Opencast.Annotation_Comment_List.show();
      }
    }

    function setCurrentClipshow(clipshowId) {
      currentClipshow = clipshowId;
    }

    function getCurrentClipshow() {
      return currentClipshow;
    }

    function switchToSeries(seriesId)
    {
      Opencast.Player.addEvent("SWITCH_SERIES_" + seriesId);
      currentSeries = seriesId;
      Opencast.clipshow.series.core.switchToSeries(seriesId, true);
    }

    function doToggleClipshow()
    {
      if (Opencast.clipshow.ui.isShown())
      {
        Opencast.clipshow.ui.hide();
      } 
      else
      {
        Opencast.clipshow.ui.show();
      }
    }

    function doToggleClipshowEditor()
    {
      if (Opencast.clipshow.editor.isShown())
      {
        Opencast.clipshow.editor.hide();
      } 
      else
      {
        Opencast.clipshow.editor.show();
      }
    }

    function setCurrentSeries(seriesId) {
      currentSeries = seriesId;
    }

    function getCurrentSeries() {
      return currentSeries;
    }

    function nextInSeries() {
      if (currentSeries) {
        Opencast.clipshow.series.core.next(currentClipshow);
      }
    }
        
    /**
     * @memberOf Opencast.Analytics
     * @description Binds the Window-Resize-Event
     */
    function initResizeEnd()
    {
        if(!resizeEndTimeoutRunning)
        {
          $(window).resize(function ()
          {
              dateIn = new Date();
              if (Opencast.clipshow.editor.isShown())
              {
                  if (resizeEndTimeoutRunning === false)
                  {
                      resizeEndTimeoutRunning = setTimeout(editorResizeEnd, waitForMove);
                  }
              }
          });
        }
        // If window has been resized
        if(resizeEndTimeoutRunning == true)
        {
            resizeEndTimeoutRunning = false;
            Opencast.clipshow.ui.draw();
            Opencast.clipshow.editor.redraw();
        }
    }

    function editorResizeEnd()
    {
        var dateOut = new Date();
        // if the Resize-Event is not over yet: set new timeout
        if ((dateOut - dateIn) < waitForMove)
        {
            setTimeout(editorResizeEnd, waitForMove);
        }
        else
        {
            resizeEndTimeoutRunning = true;
            initResizeEnd();
        }
    }
    
    return {
        initialize: initialize,
        doToggleClipshow: doToggleClipshow,
        doToggleClipshowEditor: doToggleClipshowEditor,
        switchToClipshow: switchToClipshow,
        setClipshowList: setClipshowList,
        getClipshowList: getClipshowList,
        refreshClipshowList: refreshClipshowList,
        updateUserCredentials: updateUserCredentials,
        setUserCredentials: setUserCredentials,
        getUserId: getUserId,
        getUsername: getUsername,
        setCurrentSeries: setCurrentSeries,
        getCurrentSeries: getCurrentSeries,
        nextInSeries: nextInSeries,
        getCurrentClipshow: getCurrentClipshow,
        setCurrentClipshow: setCurrentClipshow
    };
}());

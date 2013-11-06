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
Opencast.clipshow.series = Opencast.clipshow.series || {};

/**
 * @namespace the global Opencast namespace Description
 */
Opencast.clipshow.series.core = (function ()
{

    var seriesTabDisplayed = false;
    // The data to display
    var seriesData = { series: {}, clipshows: {}, current: false, playing: false}; 
    // The currently playing series
    var playingSeries = false;

    /**
     * @memberOf Opencast.Series
     * @description Displays the Series Tab
     */
    function show()
    {
    	if (isShown()) {
    		Opencast.clipshow.series.ui.render();
    		return;
    	}
    	Opencast.Player.addEvent("SERIES_TAB_SHOW");
        Opencast.Plugin_Controller.hideAll(Opencast.clipshow.series.core);
        updateClipshowData();
        updateSeriesData();
        // Change Tab Caption
        $('#oc_btn-clipshow-series').attr(
        {
            title: "Hide Clipshow Series"
        });
        $('#oc_btn-clipshow-series').html("Hide Clipshow Series");
        $("#oc_btn-clipshow-series").attr('aria-pressed', 'true');
        // Show a loading Image
        $('#oc_clipshow-series').show();
        $('#clipshow-series-loading').show();
        $('#oc-clipshow-series').hide();
        Opencast.clipshow.series.ui.addAsPlugin($('#oc-clipshow-series'));
        seriesTabDisplayed = true;
    }

    /**
     * @memberOf Opencast.clipshow.user.core
     * @description Hides the User Tab
     */
    function hide()
    {
        Opencast.Plugin_Controller.hideAll();
        Opencast.Player.addEvent("SERIES_TAB_HIDE");
    	// Change Tab Caption
        $('#oc_btn-clipshow-series').attr(
        {
            title: "Clipshow Series"
        });
        $('#oc_btn-clipshow-series').html("Clipshow Series");
        $("#oc_btn-clipshow-series").attr('aria-pressed', 'false');
        seriesTabDisplayed = false;        
    }

    function isShown() {
      return seriesTabDisplayed;
    }
    
    /**
     * @memberOf Opencast.clipshow.user.core
     * @description Toggles the User Tab
     */
    function doToggle()
    {
        if (!isShown())
        {
            Opencast.Plugin_Controller.hideAll();
            show();
        }
        else
        {
            hide();
        }
    }

    function getSeriesData() {
      return seriesData;
    }

    function next(currentClipshowId) {
      if (seriesData.playing) {
        for (var i = 0; i < seriesData.playing.clipshows.length; i++) {
          if (seriesData.playing.clipshows[i].id == currentClipshowId) {
            if (i < seriesData.playing.clipshows.length - 1) {
              //Do redirect to next one
              window.location.href = "./watch.html?id=" + seriesData.playing.clipshows[i+1].mediapackageId + "&clipshowId=" + seriesData.playing.clipshows[i+1].id + "&clipshowSeriesId=" + seriesData.playing.id + "&statsEnabled=" + Opencast.Analytics.isVisible();
            } else {
              //This is the last one in the series, so pause playback
              Opencast.Player.doPause();
            }
          }
        }
      }
    }

    function updateSeriesData() {
      seriesData.seriesDone = false;
      //TODO:  Search for series for which the current user is the author...
      //Get the list of series
      $.ajax(
      {
        type: "GET",
        url: "../../clipshow/series/list",
        dataType: 'json',
        success: function (json)
        {
          var results = json.wrapper.data;
          if ('undefined' != typeof results) {
              if ($.isArray(results)) {
                seriesData.series = results;
              } else {
                seriesData.series = [results];
              }
          } else {
            seriesData.series = [];
          }
          seriesData.seriesDone = true;
        },
        error: function (a, b, c)
        {
            seriesData.series = [];
        }
      });
    }

    function updateClipshowData() {
      seriesData.clipshowsDone = false;
      //Get the list of clipshows for the current media
      $.ajax(
      {
        type: "GET",
        url: "../../clipshow/list",
        dataType: 'json',
        success: function (json)
        {
          var results = json.wrapper.data;
          if ('undefined' != typeof results) {
              if ($.isArray(results)) {
                seriesData.clipshows = results;
              } else {
                seriesData.clipshows = [results];
              }
          } else {
        	  seriesData.clipshows = [];
          }
          seriesData.clipshowsDone = true;
          if (isShown()) {
        	  Opencast.clipshow.series.ui.render();
          }
        },
        error: function (a, b, c)
        {
          seriesData.clipshowsDone = true;
        	seriesData.clipshows = [];
        }
      });
    }

    function setCurrentSeries(data) {
      seriesData.current = data;
    }

    function setPlayingSeries(data) {
      seriesData.playing = data;
    }

    function playSeries() {
      seriesData.playing = seriesData.current;
      Opencast.Player.addEvent("PLAY_SERIES_" + seriesData.playing.id);
      window.location.href = "./watch.html?id=" + seriesData.playing.clipshows[0].mediapackageId + "&clipshowId=" + seriesData.playing.clipshows[0].id + "&clipshowSeriesId=" + seriesData.playing.id;
    }

    function stopSeries() {
      Opencast.Player.addEvent("STOP_SERIES_" + seriesData.playing.id);
      seriesData.playing = false;
      if (Opencast.clipshow.series.core.isShown()) {
      	Opencast.clipshow.series.ui.render();
      }
    }

    function createSeries(title, description, clipshows) {
      $.ajax(
      {
        type: "POST",
        url: "../../clipshow/series/create",
        data: JSON.stringify({title: title, description: description, clipshows: clipshows}),
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function (json)
        {
          Opencast.Player.addEvent("CREATE_SERIES_" + title);
          var newListItem = '<option value="' + json + '">' + $('#oc_clipshow_series_title').val() + ' by ' + Opencast.clipshow.core.getUsername() + '</option>';
          $("#oc_clipshow_series_select").append(newListItem);
          $("#oc_clipshow_series_select").val(json);
          $("#oc_clipshow_series_play").button({disabled: false});
          updateSeriesData();
        }
      });
    }

    function updateSeries(id, title, description, clipshows) {
    $.ajax({
        type: "POST",
        url: "../../clipshow/series/update",
        data: JSON.stringify({id: id, title: title, description: description, clipshows: clipshows}),
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function (id)
        {
          updateSeriesData();
          //updateClipshowData();
          Opencast.Player.addEvent("UPDATE_SERIES_" + title);
          switchToSeries(id, false);
        }
      });
    }

    function switchToSeries(seriesId, playing) {
      if (seriesId != false) {
        $.ajax({
            type: "GET",
            url: "../../clipshow/series/get?seriesId=" + seriesId,
            dataType: 'json',
            success: function(json) {
              Opencast.Player.addEvent("VIEW_SERIES_" + seriesId);
              var series = json["clipshow-series"];
              
              //Series which have no clipshows yet do not carry a list of clipshows
              if ('undefined' == typeof series.clipshows) {
                series.clipshows = [];
              }

              //Make sure to put things in an array, sigh...
              if (!$.isArray(series.clipshows)) {
                series.clipshows = [series.clipshows];
              }

              setCurrentSeries(series);
              if (playing) {
                setPlayingSeries(series);
              }
              show();
            }
        });
      } else {
        setCurrentSeries(false);
        setPlayingSeries(false);
        if (isShown()) {
          Opencast.clipshow.series.ui.render();
        } else {
          show();
        }
      }
    }

    function deleteSeries(seriesId) {
	    $.ajax(
	    {
	      type: "POST",
	      url: "../../clipshow/series/delete",
	      data: 'seriesId=' + seriesId,
	      dataType: 'json',
	      success: function (json)
	      {
	    	Opencast.Player.addEvent("DELETE_SERIES_" + seriesId);
	    	seriesData.seriesDone = false;
	    	updateSeriesData();
	        switchToSeries(false);

	      }
	    });
    }

    return {
        show: show,
        hide: hide,
        isShown: isShown,
        doToggle: doToggle,
        getSeriesData: getSeriesData,
        updateClipshowData: updateClipshowData,
        next: next,
        playSeries: playSeries,
        stopSeries: stopSeries,
        createSeries: createSeries,
        updateSeries: updateSeries,
        switchToSeries: switchToSeries,
        deleteSeries: deleteSeries
    };
}());

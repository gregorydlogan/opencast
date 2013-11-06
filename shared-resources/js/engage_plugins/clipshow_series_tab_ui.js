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
 * @namespace the global Opencast namespace Description_Plugin
 */
Opencast.clipshow.series.ui = (function ()
{
    var clipshowsTemplate =  '{for c in clipshows}' +
                               '<li id="${c.id}" style="width: 150px; height: 75px; float: left" class="ui-state-default">${c.title} by ${c.author} in ${c.mediapackageTitle}</li>' +
                             '{/for}';

    // The Template to process
    var template =  '<div style="float: left; width: 50%; height: 70%;">' +
    					'<table>' +
    						'<tr>' +
    							'<td>' +
    								'<button id="oc_clipshow_series_new">New</button>' +
    							'</td><td>' +
    								'<label for="oc_clipshow_series_select" style="float: left;">Series:</label>' + 
								'</td><td>' +
									'<select id="oc_clipshow_series_select" style="float: left;">' +
		                              '<option value="x">--</option>' +
		                              '{for s in series}' + 
		                                '<option value="${s.id}">${s.title} by ${s.author}</option>' +
		                              '{/for}' +
		                            '</select>' +
    							'</td><td>' +
    								'<button id="oc_clipshow_series_delete" disabled="true">Delete</button>' +
    							'</td>' +
    						'</tr><tr>' +
								'<td>' +
									'<p style="margin: 0px;">&nbsp;</p>' +
								'</td><td>' +
		                          '<label for="oc_clipshow_series_title" style="float: left;">Title:</label>' +
	                            '</td><td colspan="2">' +
		                          '{if (typeof current == "undefined")}' +
		                            '<input type="text" id="oc_clipshow_series_title" style="float: left;" disabled="true">' +
		                          '{else}' +
		                            '<input type="text" id="oc_clipshow_series_title" style="float: left;" value="${current.title}">' +
		                          '{/if}' +
		                        '</td>' +
		                    '</tr><tr>' +
								'<td>' +
									'<p style="margin: 0px;">&nbsp;</p>' +
								'</td><td>' +
		                    		'<label for="oc_clipshow_series_description" style="float: left;">Description:</label>' +
	                    		'</td><td colspan="2">' +
		                          '{if (typeof current == "undefined")}' +
		                            '<textarea id="oc_clipshow_series_description" style="float: left; width: 300px; height: 100px;" disabled="true"></textarea>' +
		                          '{else}' +
		                            '<textarea id="oc_clipshow_series_description" style="float: left; width: 300px; height: 100px;">${current.description}</textarea>' +
		                          '{/if}' +
	                            "</td>" +
	                        '</tr><tr>' +
								'<td>' +
									'<p style="margin: 0px;">&nbsp;</p>' +
								'</td><td>' +
	                        		'<button id="oc_clipshow_series_play" disabled="true">Play</button>' +
                        		'</td><td>' +
                        			'<button id="oc_clipshow_series_save" disabled="true">Save</button>' +
                    			'</td><td>' +
                    				'<img id="oc_clipshow_series_progress" src="/engage/ui/img/misc/squares.gif"/>' +
                    			'</td>' +
                			'</tr>' +
    					'</table>' +
                    '</div>' +
                    '<div style="float: left; width: 49%; min-height: 75px; max-height: 225px; border: 1px solid black; overflow-y: auto;">' +
                      '<ul id="oc_clipshow_series_available" class="oc_clipshow_series_connected_sortable" style="list-style-type: none; width: 100%; height: 100%; min-width: 150px; min-height: 75px; float: left; margin: 0px; padding: 0px;">' +
                      '</ul>' +
                    '</div>' +
                    '<div style="float: left; width: 99%; border: 1px solid black; min-height: 75px;">' +
                      '<ol id="oc_clipshow_series_selected" class="oc_clipshow_series_connected_sortable" style="list-style-type: none; margin: 0px; width: 100%; min-height: 75px;">' +
                        '{if ("undefined" != typeof current)}' +
                          '{for c in current.clipshows}' +
                            '<li id="${c.id}" style="width: 150px; height: 75px; float: left;" class="ui-state-default">${c.title} by ${c.author.name}</li>' +
                          '{/for}' +
                        '{/if}' +
                      '</ol>' +
                    '</div>' +
                    '<div style="clear: both">' + 
                    '</div>';
                    
    // The Element to put the div into
    var element;
    // Precessed Data
    var processedTemplateData = false;
    // The original selected series 
    var originalSeries = false;
    
    /**
     * @memberOf Opencast.clipshow.user.ui
     * @description Add As Plug-in
     * @param elem Element to fill with the Data (e.g. a div)
     * @param data The data to fill the tab with
    */
    function addAsPlugin(elem)
    {
        element = elem;
        render();
    }

    function generateAvailableClipshows(seriesData) {
      //TODO:  Add logging around this somehow, this could get expensive in a *real* hurry
      if (seriesData.current) {
        var available = [];
        $.each(seriesData.clipshows, function() {
          for (var i=0; i < seriesData.current.clipshows.length; i++) {
            if (this.id == seriesData.current.clipshows[i].id) {
              return;
            }
          }
          available.push(this);
        });
        return available;
      } else {
        return seriesData.clipshows;
      }
    }

    function setupSortables() {
      //Setup the sortable lists and connect them
      $('.oc_clipshow_series_connected_sortable').sortable({
        connectWith: ".oc_clipshow_series_connected_sortable",
        containment: "#oc-clipshow-series",
        placeholder: "ui-state-highlight"
      }).disableSelection();
      //  appendTo: "#oc-clipshow-series",

    }

    function selectSeriesHandler(noPrompt) {
      var seriesId = $("#oc_clipshow_series_select option:selected").val();
      if (seriesId != "x") {
        if (Opencast.clipshow.series.core.getSeriesData().playing != false && noPrompt != false) {
			$( "#oc_clipshow-series-confirm-dialog" ).dialog({
				resizable: false,
				height:150,
				modal: true,
				buttons: {
					"Stop series playback": function() {
						Opencast.clipshow.series.core.stopSeries();
						Opencast.clipshow.series.core.switchToSeries(seriesId);
			            $( this ).dialog( "close" );
					},
					Cancel: function() {
						if (originalSeries != false) {
							$("#oc_clipshow_series_select").val(originalSeries);
						}
						$( this ).dialog( "close" );
					}
				}
			});
        } else {
        	Opencast.clipshow.series.core.switchToSeries(seriesId);
        }
      } else {
        Opencast.clipshow.series.core.switchToSeries(false);
      }
    }

    function newSeriesHandler() {
  	  Opencast.Player.addEvent("SERIES_TAB_NEW_SERIES");
      //Assumption, to have rendered the button such that it can be clicked on, we won't be in a state where this will return bad data.
      var seriesData = Opencast.clipshow.series.core.getSeriesData();
      $("#oc_clipshow_series_title").attr("disabled", false).val("New Title");
      $("#oc_clipshow_series_description").attr("disabled", false).val("New Description");
      $("#oc_clipshow_series_select").val("x");
      Opencast.clipshow.series.core.switchToSeries(false);
      processedTemplateData = clipshowsTemplate.process({ clipshows: generateAvailableClipshows(seriesData)});
      $('#oc_clipshow_series_available').html(processedTemplateData).attr("disabled", false);
      $('#oc_clipshow_series_selected').html("").attr("disabled", false);
      $("#oc_clipshow_series_save").button({disabled: false});
      setupSortables();
    }

    function seriesSaveHandler() {
      var title = $('#oc_clipshow_series_title').val();
      var description = $('#oc_clipshow_series_description').val();
      var seriesArray = $('#oc_clipshow_series_selected').sortable("toArray");
      if (title == "") {
        alert("Please enter a title for your series");
        return;
      }

      $('#oc_clipshow_series_progress').show();
      setTimeout(function() {$('#oc_clipshow_series_progress').hide();}, 1000);

      //If this is a *new* series
      if ($("#oc_clipshow_series_select").val() == "x") {
        Opencast.clipshow.series.core.createSeries(title, description, seriesArray);
      } else {
        Opencast.clipshow.series.core.updateSeries($("#oc_clipshow_series_select").val(), title, description, seriesArray);
      }
    }

    function seriesStartHandler() {
      Opencast.clipshow.series.core.switchToSeries($("#oc_clipshow_series_select option:selected").val());
      Opencast.clipshow.series.core.playSeries();
      $("#oc_clipshow_series_play").button({label: "Stop"}).click(Opencast.clipshow.core.stopSeries);
    }

    function deleteSeriesHandler() {
      if (Opencast.clipshow.series.core.getSeriesData().current != false) {
        var confirmation = confirm("You are about to delete this clipshow series, do you wish to continue?");
        if (confirmation) {
          Opencast.clipshow.series.core.deleteSeries(Opencast.clipshow.series.core.getSeriesData().current.id);
        }
      }
    }

    function render() {
      var seriesData = Opencast.clipshow.series.core.getSeriesData();
      if (!(seriesData.seriesDone && seriesData.clipshowsDone)) {
        setTimeout("Opencast.clipshow.series.ui.render()", 250);
        return;
      }

      if (seriesData.playing != false) {
        originalSeries = seriesData.playing.id;
      }
      processedTemplateData = template.process(seriesData);
      element.html(processedTemplateData);
      $('#oc-clipshow-series').show();
      $('#clipshow-series-loading').hide();
      $('#oc_clipshow_series_progress').hide();

      processedTemplateData = clipshowsTemplate.process({ clipshows: generateAvailableClipshows(seriesData)});
      $('#oc_clipshow_series_available').html(processedTemplateData);

      //Save button
      $("#oc_clipshow_series_save").button().click(seriesSaveHandler);

      //Play button
      if (Opencast.clipshow.series.core.getSeriesData().playing == false) {
        $("#oc_clipshow_series_play").button({label: "Play"}).click(seriesStartHandler);
      } else {
        $("#oc_clipshow_series_play").button({label: "Stop"}).click(Opencast.clipshow.series.core.stopSeries);
      }

      //New button
      $("#oc_clipshow_series_new").button().click(newSeriesHandler);

      $("#oc_clipshow_series_delete").button().click(deleteSeriesHandler);

      //Switching various controls on/off depending on what the current series is
      if (seriesData.current) {
        $("#oc_clipshow_series_select").val(seriesData.current.id);
	    if (Opencast.clipshow.core.getUserId() == seriesData.current.author.id) {
          setupSortables();
          $("#oc_clipshow_series_save").button({disabled: false});
          $("#oc_clipshow_series_delete").button({disabled: false});
        } else {
          $("#oc_clipshow_series_title").attr("disabled", true);
          $("#oc_clipshow_series_description").attr("disabled", true);
          $("#oc_clipshow_series_delete").button({disabled: true});
        }
        $("#oc_clipshow_series_play").button({disabled: false});
      } else {
        $("#oc_clipshow_series_save").button({disabled: true});
        $("#oc_clipshow_series_play").button({disabled: true});
        $("#oc_clipshow_series_delete").button({disabled: true});
      }

      //The series selection tool.
      $("#oc_clipshow_series_select").change(selectSeriesHandler);

      $('.oc_clipshow_series_connected_sortable').disableSelection();
      return true;
    }

    return {
        addAsPlugin: addAsPlugin,
        render: render
    };
}());

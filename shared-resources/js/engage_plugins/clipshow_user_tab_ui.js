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
Opencast.clipshow.user = Opencast.clipshow.user || {};

/**
 * @namespace the global Opencast namespace Description_Plugin
 */
Opencast.clipshow.user.ui = (function ()
{
    // The Template to process
    var template =  '<div style="float: left; width: 80%; height: 100%; overflow: hidden;">' + 
    					'<div style="width: 100%; height: 10%">' +
	                        '<form id="oc-form-clipshow-search">' +
	                        '<p>Search ' +
	                          '<select id="oc-form-clipshow-search-type">' +
	                            '<option value="titles">titles</option>' +
	                            '<option value="authors">authors</option>' +
                                '<option value="tags">tags</option>' +
	                          '</select>' +
	                          ' for ' +
	                          '<input id="oc-form-clipshow-search-tag" type="text" name="search" value="${searchTerm}" style="width: 150px;"/>' +
	                          '<select id="oc-form-clipshow-search-restrict">' +
	                            '<option value="false">for all videos</option>' +
	                            '<option value="true">within this video</option>' +
	                          '</select>' +
	                          ' and sort by ' + 
	                          '<select id="oc-form-clipshow-search-sort">' +
	                            '<option value="GOOD">good</option>' +
	                            '<option value="FUNNY">funny</option>' +
	                          '</select>' +
	                          ' ratings ' +
	                        '</p>' +
	                    '</div>' +
	                    '<div style="float: left; width: 100%; height: 90%">' +
	                      '<ol id="oc_usertab-column-one" style="float:left; width: 30%; height: 100%;">' +
	                        '{for c in clipshows.firstColumn}' +
	                          '<li><button class="clipshow-link-button" onclick="Opencast.clipshow.core.switchToClipshow(${c.id})">${c.title} by ${c.author}</button></li>' +
	                        '{/for}' +
	                      '</ol>' +
	                      '<ol id="oc_usertab-column-two" style="float:left; width: 30%; height: 100%;" start="${columnLength + 1}">' +
	                        '{for c in clipshows.secondColumn}' +
	                          '<li><button class="clipshow-link-button" onclick="Opencast.clipshow.core.switchToClipshow(${c.id})">${c.title} by ${c.author}</button></li>' +
	                        '{/for}' +
	                      '</ol>' +
	                      '<ol id="oc_usertab-column-three" style="float:left; width: 30%; height: 100%;" start="${(2 * columnLength) + 1}">' +
	                        '{for c in clipshows.thirdColumn}' +
	                          '<li><button class="clipshow-link-button" onclick="Opencast.clipshow.core.switchToClipshow(${c.id})">${c.title} by ${c.author}</button></li>' +
	                        '{/for}' +
	                      '</ol>' +
	                    '</div>' +
	                  '</div>' +
	                  '<div style="width: 18%; height: 100%; float: left; padding: 2px; border: 1px solid black;">' +
	                  	'<p>Your current username is: </p>' +
	                  	'<input id="oc_usertab-username" title="current username" name="current username" value=${displayName} style="margin: 2px;">' +
	                  	'<button id="oc_usertab-username-change">' +
	                  '</div>';
                    
    // The Element to put the div into
    var element;
    // Data to process
    var user_data;
    // Precessed Data
    var processedTemplateData = false;
    
    /**
     * @memberOf Opencast.clipshow.user.ui
     * @description Add As Plug-in
     * @param elem Element to fill with the Data (e.g. a div)
     * @param data The data to fill the tab with
    */
    function addAsPlugin(elem)
    {
        element = elem;

        init();
    }

    function init()
    {
      if (Opencast.clipshow.core.getUsername() == undefined) {
          setTimeout("Opencast.clipshow.user.ui.init()", 250);
          return;
      }

      user_data = {searchTerm: "", sortBy: "GOOD", searchType: "titles", restrictedTo: "false", displayName: Opencast.clipshow.core.getUsername()};

      render();
    }

    function handleSuccessfulSearch(json) {
      var results = json.wrapper.data;
      if ('undefined' != typeof results) {
        if ($.isArray(results)) {
          user_data.searches = results;
        } else {
          user_data.searches = [results];
        }
      } else {
        user_data.searches = [];
      }
      render();
    }

    function handleFailedSearch(json) {
      user_data.searches = [];
      render();
    }

    function refreshSearchData() {
      user_data.searchTerm = $('#oc-form-clipshow-search-tag').val();
      data = {parameter: user_data.searchTerm};
      
      if (user_data.restrictedTo == 'true') {
        data.mediapackageId = Opencast.Player.getMediaPackageId();
      }
      Opencast.Player.addEvent("USER_TAB_SEARCH_" + $('#oc-form-clipshow-search-type').val() + "_" + data.parameter + "_" + user_data.restrictedTo);
      $.ajax(
      {
          type: "GET",
          url: "../../clipshow/search/" + $('#oc-form-clipshow-search-type').val(),
          data: data,
          dataType: 'json',
          success: handleSuccessfulSearch,
          error: handleFailedSearch
      });
    }

    function changeUsername() {
      $.ajax(
      {
          type: "POST",
          url: "../../clipshow/user/newName",
          data: {newName: $('#oc_usertab-username').val()},
          dataType: 'json',
          success: function (json)
          {
              user_data.displayName = $('#oc_usertab-username').val();
        	  Opencast.Player.addEvent("USER_TAB_NEWNAME_" + user_data.displayName);
              Opencast.clipshow.core.updateUserCredentials();
              Opencast.clipshow.core.refreshClipshowList();
              setTimeout('Opencast.clipshow.user.ui.render()', 250);
          },
          error: function (a, b, c)
          {
              render();
          }
      });
    }

    /**
     * @memberOf Opencast.clipshow.user.ui
     * @description Processes the Data and puts it into the Element
     * @return true if successfully processed, false else
     */
    function render()
    {
        if ('undefined' != typeof element)
        {
          var sortBy = user_data.sortBy;
          var restrictedTo = user_data.restrictedTo;
          var searchType = user_data.searchType;
          var clist = [];
          if ('undefined' != typeof user_data.searches && user_data.searches != []) {
            clist = user_data.searches;
          } else {
            clist = Opencast.clipshow.core.getClipshowList();
          }            

          //Sort by dislike first so those end up at the top
          clist.sort(function(a,b) { return parseInt(b.dislike) - parseInt(a.dislike) } );
          if (sortBy == "GOOD") {
            clist.reverse();
            clist.sort(function(a,b) { return parseInt(b.funny) - parseInt(a.funny) } );
            clist.sort(function(a,b) { return parseInt(b.good) - parseInt(a.good) } );
          } else if (sortBy == "FUNNY") {
            clist.reverse();
            clist.sort(function(a,b) { return parseInt(b.good) - parseInt(a.good) } );
            clist.sort(function(a,b) { return parseInt(b.funny) - parseInt(a.funny) } );
          } else if (sortBy == "DISLIKE") {
            //Sorted above, so pass?
          }
          //Sort into three columns, divide clipshows as evenly as possible
          var columnSize = Math.max(Math.round(clist.length / 3), 1);
          user_data.columnLength = columnSize;
          var clipshows = { firstColumn: [], secondColumn: [], thirdColumn: []};
          for (var i = 0; i < columnSize; i++) {
            if ('undefined' == typeof clist[i]) {
              break;
            }
            clipshows.firstColumn[i] = clist[i];
          }
          for (var j = 0; j < columnSize; j++) {
            if ('undefined' == typeof clist[j + columnSize]) {
              break;
            }
            clipshows.secondColumn[j] = clist[j + columnSize];
          }
          for (var k = 0; k < columnSize; k++) {
            if ('undefined' == typeof clist[k + (2 * columnSize)]) {
              break;
            }
            clipshows.thirdColumn[k] = clist[k + (2 * columnSize)];
          }
          user_data.clipshows = clipshows;

          $.log("User Plugin: Data available, processing template");
          processedTemplateData = template.process(user_data);
          element.html(processedTemplateData);
          $('#oc-clipshow-user').show();
          $('#clipshow-user-loading').hide();
          $(".clipshow-link-button").button();

          $('#oc-form-clipshow-search-type').val(searchType);
          $('#oc-form-clipshow-search-type').change(function() {
            user_data.searchType = $("#oc-form-clipshow-search-type option:selected").val();
          });

          $('#oc-form-clipshow-search-restrict').val(restrictedTo);
          $('#oc-form-clipshow-search-restrict').change(function() {
            user_data.restrictedTo = $("#oc-form-clipshow-search-restrict option:selected").val();
          });

          $('#oc-form-clipshow-search-sort').val(sortBy);
          $("#oc-form-clipshow-search-sort").change(function() {
            user_data.sortBy = $("#oc-form-clipshow-search-sort option:selected").val();
            render();
          });

          $("#oc_usertab-username-change").button({label: "Change"}).click(changeUsername);

          //$('#oc-input-tagged-button').button();
          $('#oc-form-clipshow-search').submit(function ()
          {
            refreshSearchData();
            return false;
          });
          return true;
        }
        else
        {
            $.log("User Plugin: No data available");
            return false;
        }
    }

    function randomInt(from, to){
       return Math.floor(Math.random() * (to - from + 1) + from);
    }

    
    return {
        addAsPlugin: addAsPlugin,
        init: init,
        render: render
    };
}());

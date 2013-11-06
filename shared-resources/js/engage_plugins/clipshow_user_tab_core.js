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
 * @namespace the global Opencast namespace Description
 */
Opencast.clipshow.user.core = (function ()
{
    var userTabDisplayed = false;
    /**
     * @memberOf Opencast.clipshow.user.core
     * @description Displays the User Tab
     */
    function show()
    {
        Opencast.Plugin_Controller.hideAll(Opencast.clipshow.user.core);
        Opencast.Player.addEvent("USER_TAB_SHOW");
        // Change Tab Caption
        $('#oc_btn-clipshow-user').attr(
        {
            title: "Hide Clipshow User"
        });
        $('#oc_btn-clipshow-user').html("Hide Clipshow User");
        $("#oc_btn-clipshow-user").attr('aria-pressed', 'true');
        // Show a loading Image
        $('#oc_clipshow-user').show();
        $('#clipshow-user-loading').show();
        $('#oc-clipshow-user').hide();
        Opencast.clipshow.user.ui.addAsPlugin($('#oc-clipshow-user'));
        userTabDisplayed = true;
    }

    /**
     * @memberOf Opencast.clipshow.user.core
     * @description Hides the User Tab
     */
    function hide()
    {
    	Opencast.Player.addEvent("USER_TAB_HIDE");
        // Change Tab Caption
        $('#oc_btn-clipshow-user').attr(
        {
            title: "Clipshow User"
        });
        $('#oc_btn-clipshow-user').html("Clipshow User");
        $("#oc_btn-clipshow-user").attr('aria-pressed', 'false');
        $('#oc_clipshow-user').hide();
        userTabDisplayed = false;
    }

    function isShown() {
      return userTabDisplayed;
    }

    /**
     * @memberOf Opencast.clipshow.user.core
     * @description Toggles the User Tab
     */
    function doToggle()
    {
        if (!isShown())
        {
            Opencast.Plugin_Controller.hideAll(Opencast.clipshow.user.core);
            show();
        }
        else
        {
            hide();
        }
    }
    
    return {
        show: show,
        hide: hide,
        isShown: isShown,
        doToggle: doToggle
    };
}());

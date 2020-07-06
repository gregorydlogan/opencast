/**
 * Copyright 2009-2011 The Regents of the University of California Licensed
 * under the Educational Community License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.osedu.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
/*jslint browser: true, nomen: true*/
/*global define*/
define(["jquery", "underscore", "backbone", "engage/core"], function($, _, Backbone, Engage) {
    "use strict";

    var insertIntoDOM = true;
    var PLUGIN_NAME = "Transcript";
    var PLUGIN_TYPE = "engage_tab";
    var PLUGIN_VERSION = "1.0";
    var PLUGIN_TEMPLATE_DESKTOP = "templates/desktop.html";
    var PLUGIN_TEMPLATE_MOBILE = "templates/mobile.html";
    var PLUGIN_TEMPLATE_EMBED = "templates/embed.html";
    var PLUGIN_STYLES_DESKTOP = [
        "styles/desktop.css"
    ];
    var PLUGIN_STYLES_EMBED = [
        "styles/embed.css"
    ];
    var PLUGIN_STYLES_MOBILE = [
        "styles/mobile.css"
    ];

    var plugin;
    var events = {
        plugin_load_done: new Engage.Event("Core:plugin_load_done", "", "handler"),
        mediaPackageModelError: new Engage.Event("MhConnection:mediaPackageModelError", "", "handler"),
        timeupdate: new Engage.Event('Video:timeupdate', 'notices a timeupdate', 'handler'),
        seek: new Engage.Event('Video:seek', 'seek video to a given position in seconds', 'trigger')
    };

    var isDesktopMode = false;
    var isEmbedMode = false;
    var isMobileMode = false;

    // desktop, embed and mobile logic
    switch (Engage.model.get("mode")) {
        case "embed":
            plugin = {
                insertIntoDOM: insertIntoDOM,
                name: PLUGIN_NAME,
                type: PLUGIN_TYPE,
                version: PLUGIN_VERSION,
                styles: PLUGIN_STYLES_EMBED,
                template: PLUGIN_TEMPLATE_EMBED,
                events: events
            };
            isEmbedMode = true;
            break;
        case "mobile":
            plugin = {
                insertIntoDOM: insertIntoDOM,
                name: PLUGIN_NAME,
                type: PLUGIN_TYPE,
                version: PLUGIN_VERSION,
                styles: PLUGIN_STYLES_MOBILE,
                template: PLUGIN_TEMPLATE_MOBILE,
                events: events
            };
            isMobileMode = true;
            break;
        case "desktop":
        default:
            plugin = {
                insertIntoDOM: insertIntoDOM,
                name: PLUGIN_NAME,
                type: PLUGIN_TYPE,
                version: PLUGIN_VERSION,
                styles: PLUGIN_STYLES_DESKTOP,
                template: PLUGIN_TEMPLATE_DESKTOP,
                events: events
            };
            isDesktopMode = true;
            break;
    }

    /* don't change these variables */
    var viewsModelChange = "change:views";
    var mediapackageChange = "change:mediaPackage";
    var initCount = 4;
    var mediapackageError = false;
    var translations = new Array();
    var Utils;
    var Parser;
    var vttObjects = {};
    var currentTime = 0;
    var startTime = "startTime";
    var endTime = "endTime";
    var text = "text";
    var sentencesCount = 0;

    function initTranslate(language, funcSuccess, funcError) {
        var path = Engage.getPluginPath("EngagePluginTabTranscript").replace(/(\.\.\/)/g, "");
        var jsonstr = window.location.origin + "/engage/theodul/" + path;

        Engage.log("Tab:Transcript: Choosing english translations");
        jsonstr += "language/en.json";

        $.ajax({
            url: jsonstr,
            dataType: "json",
            async: false,
            success: function (data) {
                if (data) {
                    data.value_locale = language;
                    translations = data;
                    if (funcSuccess) {
                        funcSuccess(translations);
                    }
                } else {
                    if (funcError) {
                        funcError();
                    }
                }
            },
            error: function (jqXHR, textStatus, errorThrown) {
                if (funcError) {
                    funcError();
                }
            }
        });
    }

    function translate(str, strIfNotFound) {
        return (translations[str] != undefined) ? translations[str] : strIfNotFound;
    }

    function getCaptionList(model) {

        var captions = _.find(model.get('attachments'), function (item) {
            // type: "captions/timedtext", mimetype: "text/vtt"
            // assumption is that there is only one vvt file per video.
            if (item.type.indexOf('captions') >= 0 || item.mimetype == 'text/vtt') {
                item.url = window.location.protocol + item.url.substring(item.url.indexOf('/'));
                return item;
            }
        });

        return captions;
    }

    function getVTT(captions) {
        var request = new XMLHttpRequest();
        request.open('GET', captions["url"], false);
        request.send(null);

        if(request.status === 200) {
            return request.responseText;
        } else {
            console.error(request.statusText);
            return undefined;
        }
    }

    var TranscriptTabView = Backbone.View.extend({
        initialize: function (mediaPackageModel, template) {
            this.setElement($(plugin.container)); // every plugin view has it's own container associated with it
            this.model = mediaPackageModel;
            this.template = template;
            // bind the render function always to the view
            _.bindAll(this, "render");
            // listen for changes of the model and bind the render function to this
            this.model.bind("change", this.render);
        },
        render: function () {
            if (!mediapackageError) {
                var vttText = [];
                var captions = getCaptionList(this.model);

                if (!_.isUndefined(captions)) {
                    var vtt = getVTT(captions);

                    if(vtt) {
                        var vttText = Parser.parse(vtt, 'metadata')['cues'];
                        for (var i = 0; i < vttText.length; i++) {
                            buildVTTObject(i, vttText[i])
                        }
                    }
                }

                var parts = [],
                    tempVars = {
                        search_str: translate("search_str", "Search"),
                        search_placeholder_str: translate("search_placeholder_str", "Search terms (space separated)"),
                        request_transcript_str: translate("request_transcript_str", "No captions or transcripts are available for this video. "),
                        request_transcript_link_text_str: translate("request_transcript_link_text_str", "Request a transcript."),
                        request_transcript_link_mail: "mailto:help@vula.uct.ac.za?Subject=Captions%20request",
                        vttObjects: vttObjects,
                        user: Engage.model.get("meInfo").get("user"),
                        mediaPackage_title: Engage.model.get('mediaPackage').get('title'),
                        mediaPackage_eventid: 'E['+Engage.model.get('mediaPackage').get('eventid')+']',
                        mediaPackage_series: Engage.model.get('mediaPackage').get('series'),
                        mediaPackage_seriesid: 'S['+Engage.model.get('mediaPackage').get('seriesid')+']',
                        mediaPackage_date: Engage.model.get('mediaPackage').get('date')
                    };

                var tpl = _.template(this.template);
                this.$el.html(tpl(tempVars));
                addListeners(vttText);
            }
        }
    });

    function requestTranscriptWorkflow() {
        $.ajax({
            url:"/api/workflows/",
            method:"POST",
            data:{
                event_identifier: Engage.model.get('mediaPackage').get('eventid'),
                workflow_definition_identifier: "uct-request-transcript",
                withoperations: false,
                withconfiguration: false,
            },
        }).done(function(response) {
            alert(response.description);
        }).fail(function( jqXHR, textStatus ) {
            console.log(textStatus);
        });
    }

    function addListeners(vttText) {
        $( "#transcript_tab_search" ).keyup(filterText);
        $( "#clear_transcript_tab_search" ).click(filterText);
        $( "#requestTranscript" ).click(requestTranscriptWorkflow);

        for (var i = 1; i < vttText.length; i++) {
            $( "#" + i ).click(updateVideo);
        }
    }

    function filterText() {
        var searchTerms = this.value.split(' ');

        if(searchTerms.length===1 && (searchTerms[0] === "" || this.id === "clear_transcript_tab_search")) {
            var nodes = document.getElementById('transcript').getElementsByTagName("span");
            for(var i=0; i<nodes.length; i++) {
                nodes[i].classList.remove("greyout");
            }
            return;
        }

        _.each(vttObjects, function (object, key) {
            var element = document.getElementById(key);

            if (!checkIfContainsAnySearchTerms(key, searchTerms)) {
                element.classList.add("greyout");
            }
            else {
                element.classList.remove("greyout");
            }
        });
    }

    function checkIfContainsAnySearchTerms(key, searchTerms) {
        for(var i=0; i<searchTerms.length; i++) {
            if(searchTerms[i] !== "" && vttObjects[key][text].toLowerCase().includes(searchTerms[i].toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    function buildVTTObject(index, vttText) {
        var timeAndTextObject = {}
        timeAndTextObject[startTime] = vttText[startTime]*1000;
        timeAndTextObject[endTime] = vttText[endTime]*1000;
        var sentence = vttText[text].replace(/\n/, " ");
        if(index != 1 && newLineRequired(sentence)) {
            timeAndTextObject[text] = sentence + "\n";
        } else {
            timeAndTextObject[text] = sentence;
        }
        vttObjects[index] = timeAndTextObject;
    }

    function newLineRequired(line) {
        //new line is required after every 10 sentences.
        var sentenceEnd = (line.match(/[\.\?\!]/g) || []).length;

        if(sentenceEnd === 0) {
            return false;
        } else {
            sentencesCount += sentenceEnd;

            if(sentencesCount >= 10) {
                sentencesCount = 0;
                return true;
            }
        }

        return false;
    }

    function getIndexByTime(arr, time) {
        var index = -1;
        $.each(arr, function (i, el) {
            if ((el[startTime] <= time) && (time < el[endTime])) {
                index=i;
                return;
            }
        });
        return index;
    }

    function updateVideo() {
        var vttObject = vttObjects[this.id]
        var time = vttObject[startTime];
        Engage.trigger(plugin.events.seek.getName(), time / 1000);
    }

    function initPlugin() {
        // only init if plugin template was inserted into the DOM
        if (isDesktopMode && plugin.inserted) {
            Engage.log("Tab:Transcript initialized");
            var transcriptTabView = new TranscriptTabView(Engage.model.get("mediaPackage"), plugin.template);
            Engage.on(plugin.events.mediaPackageModelError.getName(), function (msg) {
                mediapackageError = true;
            });
            Engage.model.get("views").on("change", function () {
                transcriptTabView.render();
            });
            transcriptTabView.render();
        }
    }

    if (isDesktopMode) {
        // init event
        Engage.log("Tab:Transcript: Init");
        var relative_plugin_path = Engage.getPluginPath("EngagePluginTabTranscript");

        // load utils class
        require([relative_plugin_path + "utils"], function (utils) {
            Engage.log("Tab:Transcript: Utils class loaded");
            Utils = new utils();
            initTranslate(Utils.detectLanguage(), function () {
                Engage.log("Tab:Transcript: Successfully translated.");
                initCount -= 1;
                if (initCount <= 0) {
                    initPlugin();
                }
            }, function () {
                Engage.log("Tab:Transcript: Error translating...");
                initCount -= 1;
                if (initCount <= 0) {
                    initPlugin();
                }
            });
        });

        // load parser class
        require([relative_plugin_path + "parser"], function (parser) {
            Engage.log("Tab:Transcript: parser class loaded");
            Parser = new WebVTTParser();
        });

        Engage.model.on(viewsModelChange, function () {
            initCount -= 1;
            if (initCount <= 0) {
                initPlugin();
            }
        });

        // listen on a change/set of the mediaPackage model
        Engage.model.on(mediapackageChange, function () {
            initCount -= 1;
            if (initCount <= 0) {
                initPlugin();
            }
        });

        // all plugins loaded
        Engage.on(plugin.events.plugin_load_done.getName(), function () {
            Engage.log("Tab:Transcript: Plugin load done");
            initCount -= 1;
            if (initCount <= 0) {
                initPlugin();
            }
        });

        Engage.on(plugin.events.timeupdate.getName(), function (_currentTime) {
            if (!mediapackageError) {
                currentTime = _currentTime;
                var index = getIndexByTime(vttObjects, currentTime*1000);
                $('.highlight span').removeClass('play');

                if (index > -1) {
                    document.getElementById(index).classList.add("play");
                }
            }
        });
    }

    return plugin;
});

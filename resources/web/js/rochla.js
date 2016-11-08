$(document).ready(function () {
        if (typeof(RochlaPageStage) !== "undefined") {
            startApp(RochlaPageStage)
        }
    }
);

function initGuag() {
    // Get display div from document
    var display = document.getElementById("machine");

    var tunnel =
        new Guacamole.WebSocketTunnel("/session/tunnel/")
    // Instantiate client, using an HTTP tunnel for communications.
    var guac = new Guacamole.Client(
        tunnel
    );

    // Add client to display div
    display.appendChild(guac.getDisplay().getElement());

    // Error handler
    guac.onerror = function (error) {
        console.error(error)
        alert("Unexpected error. This is a prototype after all =). Refresh after a while. And complain. ^.^");
    };


    // Connect
    guac.connect();

    // Disconnect on close
    window.onunload = function () {
        guac.disconnect();
    }

    // Mouse
    var mouse = new Guacamole.Mouse(guac.getDisplay().getElement());

    mouse.onmousedown =
        mouse.onmouseup =
            mouse.onmousemove = function (mouseState) {
                guac.sendMouseState(mouseState);
            };

    // Keyboard
    var keyboard = new Guacamole.Keyboard(document);

    keyboard.onkeydown = function (keysym) {
        guac.sendKeyEvent(1, keysym);
    };

    keyboard.onkeyup = function (keysym) {
        guac.sendKeyEvent(0, keysym);
    };

}

var state = undefined;
var States = {
    AlreadyExists: {
        start: function () {
            window.setTimeout(function () {

                state = States.GettingReady
                state.start()
            }, 3000)

        }
    },
    GettingReady: {
        start: function () {
            var message = 'Status: Getting machine ready';
            var timeLeft = 60;

            function checkTick() {
                timeLeft--;
                if (timeLeft < 0) {
                    $('#state-message').text(message + " (Takes longer than expected)")
                } else {
                    $('#state-message').text(message + ", " + timeLeft + " seconds left")
                }
            }

            function sheduleTick() {
                window.setTimeout(function () {
                    checkTick();
                    if (state == States.GettingReady) {
                        sheduleTick();
                    }
                }, 1000)
            }

            function checkState() {
                $.getJSON('/session/status').done(function (json) {
                    var status = json['session-state']
                    console.log("JSON Data: " + json);
                    if (status == "running") {
                        state = States.Connecting
                        state.start(json, timeLeft)
                    } else {
                        window.setTimeout(function () {
                            if (state == States.GettingReady) {
                                checkState();
                            }
                        }, 3000)
                    }
                })
                    .fail(function (jqxhr, textStatus, error) {
                        var err = textStatus + ", " + error;
                        console.log("Request Failed: " + err);
                    })
            }

            sheduleTick();
            checkState();
        }
    },
    Connecting: {
        start: function (json, time) {
            var message = 'Status: Connecting';
            var timeLeft = time;

            function checkTick() {
                timeLeft--;
                if (timeLeft < 0) {
                    $('#state-message').text(message + " (Takes longer than expected)")
                } else {
                    $('#state-message').text(message + ", " + timeLeft + " seconds left")
                }
            }

            function sheduleTick() {
                window.setTimeout(function () {
                    checkTick();
                    if (state == States.Connecting) {
                        sheduleTick();
                    }
                }, 1000)
            }

            function checkState() {
                $.post('/session/http-tunnel/?connect').done(function (id) {
                    state = States.Connected;
                    state.start(json)
                }).fail(function (jqxhr, textStatus, error) {
                    window.setTimeout(function () {
                        if (state == States.Connecting) {
                            checkState();
                        }
                    }, 1000)
                })
            }

            sheduleTick();
            checkState();
        }
    },
    Connected: {
        start: function (json) {
            $('#state-message').text("Connecting to machine");
            setTimeout(function () {
                $('#status-section').remove();
                $('#machine').css('visibility', 'visible');
            }, 2000);


            var machineStoppedIn = new Date(json['expiration-time']);


            (function () {
                var infoContent = $('<span/>');
                infoContent.append("You can directly connect via remote desktop:")
                    .append('<br/>')
                    .append("Server: " + json.url)
                    .append('<br/>')
                    .append("User: " + json.tags.user)
                    .append('<br/>')
                    .append("Password: " + json.tags.password)
                    .append('<br/>')
                    .append("Server located in: " + json.location
                        + ". Far away? Complain =)");


                var info = $('<div id="info" class="info-sections">').append(infoContent);
                $('#info').replaceWith(info);

            })();

            function updateTimeout(){
                var now = new Date();
                var differenceTS = Math.max(0, machineStoppedIn.getTime() - now.getTime())
                var difference = new Date(differenceTS);
                var mins = difference.getMinutes();

                var infoContent = $('<span/>');
                infoContent.append("This machine will be killed in " + mins + " Minutes.");

                var info = $('<div id="time-left" class="info-sections">').append(infoContent)
                $('#time-left').replaceWith(info);
            }

            function sheduleTick() {
                window.setTimeout(function () {
                    updateTimeout();


                    if (state == States.Connected) {
                        sheduleTick();
                    }
                }, 30000)
            }

            updateTimeout();
            sheduleTick();

            initGuag();
        },

    }
};

function startApp(RochlaPageStage) {
    if (States[RochlaPageStage]) {
        state = States[RochlaPageStage]
    }
    state.start()

}

function addLine(element, text) {
    $('<p/>').text(text).appendTo(element);
}
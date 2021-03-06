package controllers;

import play.*;
import play.mvc.*;
import play.libs.*;

import akka.actor.*;

import java.util.*;
import java.text.*;
import scala.concurrent.duration.Duration;

import static java.util.concurrent.TimeUnit.*;
import static play.libs.EventSource.Event.event;

import views.html.*;

public class Application extends Controller {

    final static ActorRef clock = Clock.instance;

    public static Result index() {
        return ok(index.render());
    }

    public static Result liveClock() {
        return ok(EventSource.whenConnected(es -> clock.tell(es, null)));
    }

    public static class Clock extends UntypedActor {

        final static ActorRef instance = Akka.system().actorOf(Props.create(Clock.class));

        // Send a TICK message every 100 millis
        static {
            Akka.system().scheduler().schedule(
                Duration.Zero(),
                Duration.create(100, MILLISECONDS),
                instance, "TICK",  Akka.system().dispatcher(),
                null
            );
        }

        List<EventSource> sockets = new ArrayList<EventSource>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH mm ss");

        public void onReceive(Object message) {

            // Handle connections
            if (message instanceof EventSource) {
                final EventSource eventSource = (EventSource) message;

                if (sockets.contains(eventSource)) {
                    // Browser is disconnected
                    sockets.remove(eventSource);
                    Logger.info("Browser disconnected (" + sockets.size() + " browsers currently connected)");

                } else {
                    // Register disconnected callback
                    eventSource.onDisconnected(() -> self().tell(eventSource, null));
                    // New browser connected
                    sockets.add(eventSource);
                    Logger.info("New browser connected (" + sockets.size() + " browsers currently connected)");

                }

            }
            // Tick, send time to all connected browsers
            if ("TICK".equals(message)) {
                for (EventSource es : sockets) {
                    es.send(event(dateFormat.format(new Date())));
                }
            }

        }

    }

}

-module(yamabiko_server).
-export([start_link/0]).

start_link() ->
    spawn_link(fun loop/0).

loop() ->
    receive
        {From, identity} ->
            send(From, self()),
            loop();
        {From, Term} ->
            send(From, Term),
            loop();
        Unexpected ->
            io:format("Received an unexpected message, ~s~n", [Unexpected]),
            loop()
    end.

send({Pid, Ref}, Message) ->
    Pid ! {Ref, Message}.

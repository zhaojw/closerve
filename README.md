closerve
========

CloServe is a view first web framework in Clojure. It is inspired by the [Lift web framework](http://liftweb.net)

Contrubution by:

- [Jiawei Zhao](https://github.com/zhaojw)
- [MKRRF IT Limited](http://www.mkrrf-it.com), provided some of the code used in [CloEDIT](http://www.mkrrf-it.com/cloedit/)

## Installation
Currently I have not setup a clojar download yet. You need to git clone this project to your local disk.
Then run:

```lein clean```

```lein cljsbuild once```

```lein install```

This will add CloServe to your local maven repo.

In your project that use CloServe, you can add to project.clj file dependencies:

```
[closerve "0.3.0-SNAPSHOT"]
```

[Example Code](https://github.com/zhaojw/closerve-example)

[Running Example](http://closerve.mkrrf-it.com/lazyload)

##Change from 0.2.0-SNAPSHOT to 0.3.0-SNAPSHOT
change to use immutant

##Breaking change from 0.1.0 to 0.2.0
register-lift-snippet macro's lift-instr parameter used to be: {:name SnippetName, :params {...}}
Now it is just the :params part, so for eg, in 0.1.0 code, to get a lift parameter, the call is:

```(get-in lift-instr [:params "SomeParameter"])```

Now it is:

```(lift-instr "SomeParameter")```

## Current Status
This project is in very beta stage. Quite some debug message will be thrown to the console. The apis could
change a lot in the future. Could have security issuses as well. 

It is quite functional though, so I am using it to run my own website [MKRRF IT](http://www.mkrrf-it.com).

## Acknowledgements

In addition to a lot of fantastic Clojure libraries (jetty7-websockets-async, core.async, ring,
hickory, jayq, clojurescript, crypto-random ..., a lot is learnt from 
[torus-pong](https://github.com/uswitch/torus-pong) about cljs and ws.

## License

Copyright © 2013 Jiawei Zhao, MKRRF IT Limited

Distributed under the Eclipse Public License, the same as Clojure.

The files that are marked as from third party (for eg, the js files under extern/)
belongs to the corresponding third party and have their own corresponding license.


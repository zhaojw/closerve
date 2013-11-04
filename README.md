closerve
========

CloServe is a view first web framework in Clojure. It is inspired by the [Lift web framework](http://liftweb.net)

Contrubution by:

- [Jiawei Zhao](https://github.com/zhaojw)
- [MKRRF IT Limited](http://www.mkrrf-it.com), provided some of the code used in [CloEDIT](http://www.mkrrf-it.com/cloedit/)

## Installation
Currently I have not setup a clojar download yet. You need to git clone this project to your local disk.
Then run:

```lein compile
lein install```
This will add CloServe to your local maven repo.

In your project that use CloServe, you can add to project.clj file dependencies:
```
[closerve "0.1.0-SNAPSHOT"]
```

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

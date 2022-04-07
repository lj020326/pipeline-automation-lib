#!/usr/bin/env bash


FILES="
src/adaptor
src/d3adaptor
src/descent
src/geom
src/gridrouter
src/handledisconnected
src/layout
src/layout3d
src/linklengths
src/powergraph
src/pqueue
src/rbtree
src/rectangle
src/shortestpaths
src/vpsc
src/batch
"

COLA_URL="https://ialab.it.monash.edu/webcola"

for f in ${FILES}; do
    echo "FILE=${f}"
    JS_SCRIPT="${f}.js"
    echo "JS_SCRIPT=${JS_SCRIPT}"
    FETCH_URL="${COLA_URL}/${JS_SCRIPT}"
    echo "FETCH_URL=${FETCH_URL}"
    curl -s ${FETCH_URL} -o ${JS_SCRIPT}
done




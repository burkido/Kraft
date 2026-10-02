#!/bin/zsh
# Renders every shot at a quality, a few JVMs at a time.  film/tools/render.sh final [jobs]
set -e
cd "$(dirname $0)/../.."
q=${1:-preview}; jobs=${2:-3}
./gradlew -q :film:writeClasspath
cp=$(cat film/build/classpath.txt)
shots=(open title orbs beam metal gooey voice bots image code platforms finale end)
mkdir -p film/build/logs
for s in $shots; do
  while (( $(pgrep -f "film.MainKt --shots" | wc -l) >= jobs )); do sleep 1; done
  java -Xmx3g -cp "$cp" com.burkido.kraft.film.MainKt --shots $s --quality $q > film/build/logs/$s-$q.log 2>&1 &
done
wait
for f in film/build/logs/*-$q.log(N); do tail -n1 $f; done

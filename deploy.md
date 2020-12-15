1) Update pom.xml `clojure -Spom`
2) Update version in the pom.xml file
3) Build jar `clojure -X:depstar`
4) Deploy `CLOJARS_USERNAME=darkleaf CLOJARS_PASSWORD=TOKEN clj -M:deploy`

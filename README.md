# AutomatedBuilder

Automatisches Buildsystem für Java (Buildsystem_Source):

Zum Erstellen eines ausführbaren Java-Archivs: 
> sbt assembly
Ausführen des Java-Archivs:
> java -jar target.jar /pfad/zum/projekt1 (...) /pfad/zu/projektN

Zum direkten Starten mittels SBT:
> sbt "run /pfad/zum/projekt"
bzw.
> sbt "run /pfad/zum/projekt1 (...) /pfad/zu/projektN"

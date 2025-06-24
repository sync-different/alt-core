<h1 align="center">
  <br>
  <img src="https://github.com/sync-different/.github/blob/main/alt-logo.png" alt="Alterante Core" width="250">
</h1>
<h4 align="center">A virtual filesystem + file manager written in Java.
</h4>

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](https://www.gnu.org/licenses/agpl-3.0)
[![Discord](https://img.shields.io/discord/1153355258236502046)](https://discord.com/invite/Gjw9sqYuUY)

## Get started

Welcome aboard! Follow these steps to get started.

Step 1 - Familiarize yourself with the technical documentation in the companion repo <a href="https://github.com/sync-different/alt-core-docs">alt-core-docs</a>

Step 2 - Clone this repo and follow steps to build & run alt-core (instructions below)

Step 3 - Check out the discussion board to understand the roadmap and ideas WIP <a href="https://github.com/orgs/sync-different/discussions">discussions</a>

Step 4 - Join the discord and introduce yourself to the community <a href="https://discord.com/invite/Gjw9sqYuUY">join discord</a>

## repo structure

```
alt-core
│   README.md - this file
│   LICENSE - license file (AGPL v3)
│
└───scrubber 
│   └───src - source files
│   └───repo - JAR dependency files
│   └───config - config files
│   |   │   www-bridge.properties
│   |   │   www-processor.properties
│   |   │   www-rtbackup.properties
│   |   │   www-server.properties
│   |
│   └───data - data files
│   
└───web
│   └───cass - web app files (angularJS)
```
## Requirements
- Java JDK (v17 or later)
- Maven (v3.9.x or later)
- A web browser (to run the web app)

## steps to build (maven)
```
$cd scrubber
$#install JAR dependencies in local maven repo
#./install.sh
#
#$build alt uber JAR
$./build.sh
```

## steps to run
```
$cd target
$java -jar my-app-1.0-SNAPSHOT.jar
```

## License
Distributed under the AGPL v3 License. See ``LICENSE`` file for more information.

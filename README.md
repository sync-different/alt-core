<h1 align="center">
  <br>
  <img src="https://github.com/sync-different/.github/blob/main/alt-logo.png" alt="Alterante Core" width="250">
</h1>
<h4 align="center">A virtual filesystem + file manager written in Java.
</h4>

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](https://www.gnu.org/licenses/agpl-3.0)
[![Discord](https://img.shields.io/discord/1153355258236502046)](https://discord.com/invite/Gjw9sqYuUY)

# Contributors / Thanks

A big thanks to all the contributors worldwide. This is a global effort with collaboration from these countries:
<br><br>
![USA](https://raw.githubusercontent.com/stevenrskelton/flag-icon/master/png/75/country-4x3/us.png "United States")
![Uruguay](https://raw.githubusercontent.com/stevenrskelton/flag-icon/master/png/75/country-4x3/uy.png "Uruguay")
![Pakistan](https://raw.githubusercontent.com/stevenrskelton/flag-icon/master/png/75/country-4x3/pk.png "Pakistan")

## Get started

Welcome aboard! Follow these steps to get started.

Step 1 - Familiarize yourself with the technical documentation in the companion repo <a href="https://github.com/sync-different/alt-core-docs">alt-core-docs</a>

Step 2 - Clone this repo and follow steps to build & run alt-core (instructions below)

Step 3 - Check out the discussion board to understand the roadmap and ideas WIP <a href="https://github.com/orgs/sync-different/discussions">discussions</a>

Step 4 - Join the discord and introduce yourself to the community <a href="https://discord.com/invite/Gjw9sqYuUY">join discord</a>

## repo structure

```
alt-core
│  └─── README.md - this file
│  └─── LICENSE - license file (AGPL v3)
|  └─── build-all.sh - build script
|  └─── build-clean.sh - clean build script
|  └─── clean.sh - data clean script (fresh install)
|  └─── run.sh - launch script
│
└─── cass-server-maven
│   └─── src - source files
│
└───rtserver-maven
│   └─── src - source files
|
└───scrubber-maven
│   └─── src - source files
|
└───rtserver
│   └─── config - config files  
|
└───scrubber 
│   └─── src - source files
│   └─── repo - JAR dependency files
│   └─── config - config files
│   |   │   www-bridge.properties
│   |   │   www-processor.properties
│   |   │   www-rtbackup.properties
│   |   │   www-server.properties
│   |
|   └─── data - data files
│   
└───web
    └─── cass - web app files (angularJS)

```
## Requirements
- JDK (recommended OpenJDK v17 or later)
- Maven (recommended v3.9.x or later)
- A modern web browser (to run the web app)

## Steps to build alt-core (maven)
run the script ``build_all.sh`` in the root project folder
```
$ cd alt-core
$ ./build_all.sh
```

## Steps to run alt-core

1. set the root path

set web ``root`` path in ``scrubber/config/www-server.properties``
full path required - e.g.
```
root=/Users/ale/Development/GitHub/alt-core/web
```

2. set file scan directory paths 

edit the file ``scrubber/config/scan1.txt``
full paths required, URL encoded format 
e.g. to specify path ``/Volumes/Macintosh/Users/alejandro/alterante/``
```
scandir=%2FVolumes%2FMacintosh%20HD%2FUsers%2Falejandro%2Falterante%2F;
```
3. run the first time setup script

```
$ ./setup.sh
```

4. launch the server
```
$ cd scrubber
$ java -cp target/my-app-1.0-SNAPSHOT.jar com.mycompany.app.App
```

5. open the web app on browser
```
$ open http://localhost:8081/cass/uiv3/indexv2.htm
```

6. login credentials ``user:admin`` ``password:valid``

after login, change your password in Settings!

## License
Distributed under the AGPL v3 License. See ``LICENSE`` file for more information.

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

Step 1 - Join the discord and introduce yourself to the community <a href="https://discord.com/invite/Gjw9sqYuUY">join discord</a>

Step 2 - Familiarize yourself with the technical documentation in the companion repo <a href="https://github.com/sync-different/alt-core-docs">alt-core-docs</a>

Step 3 - Clone this repo and follow steps to build & run alt-core (instructions below)

Step 4 - Check out the backlog open issues. Look for issues marked `good first issue`. these are good starting points to contribute if you are new to the project. <a href="https://github.com/sync-different/alt-core/issues">issues</a>

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
run the script ``build-all.sh`` in the root project folder
```
$ cd alt-core
$ ./build-all.sh
```

## Steps to run alt-core

1. set the root path

set web ``root`` path in ``scrubber/config/www-server.properties``
full path required - e.g.
```
root=/Users/ale/Development/GitHub/alt-core/web
```

2. run the first time setup script

```
$ ./setup.sh
```

3. launch the server on a terminal
```
$ ./run.sh
```

4. open browser and run the setup wizard to set admin password, scan folders, etc...
```
$ open http://localhost:8081/cass/index.htm
```
4.1 choose Advanced Setup, click next
4.2 page 1 - Network - set computer name, admin password, remote access, etc. Click next.
4.3 page 2 - Scan Files Folders - set the folders to scan for files for Drive #1.  Click next.
4.4 page 3 - Backup & Sync - Specify how many copies of files for backups. Keep default. Click next.
4.5 page 4 - File Types - Specify the file types you wish to scan/index or keep defaults.  Click next.
4.6 page 5 - Email configuration - Email indexing settings. Keep defaults. Click next.

5. open browser and login with your admin credentials
```
$ open http://localhost:8081/cass/uiv3/indexv2.htm
```
note: default login credentials ``user:admin`` ``password:valid``

setup wizard (step 4) will allow you to set your own admin password.

6. After few seconds, files detected in your scan folders (step 4.3) will start to appear.

## License
Distributed under the AGPL v3 License. See ``LICENSE`` file for more information.

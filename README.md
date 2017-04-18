# HaBIT Cypress to Android BLE Application

The application demonstrates connecting to a [HaBIT device](http://www.biointeractivetech.com/habit/) using the Bluetooth Low Energy APIs of android. The application performs the following tasks:
- Provides android service connection to HaBIT Device
- Listens to Broadcast updates to interact with Device
- Broadcasts JSON String packaged data from Device

The repository contains:
- an APK for easy out of the box installation
- Android project for tweaking and understanding the process of connecting to a device
- [Javadoc](https://biointeractivetechnologies.github.io/HaBIT-Cypress-to-Android-BLE-Application/) specifically for the [DaqBleManager](https://github.com/BioInteractiveTechnologies/HaBIT-Cypress-to-Android-BLE-Application/blob/master/CypressBLE/app/src/main/java/com/biointeractivetech/cypressble/DaqBleManager.java) class packaged with android project


## SwiftLint for AppCode

This simple plugin allows to highlight errors and warnings from [SwiftLint](https://github.com/realm/SwiftLint) like this:

<img src="img/swiftlint.png" width="863" alt="Example of plugin usage"/>

### Installation

You should already have SwiftLint installed somewhere.
 1. Configure SwiftLint via `.swiftlint.yml` file, if needed. Look [here](https://github.com/realm/SwiftLint#configuration) for details.
 
    (Note that the plugin works with `reporter: "csv"`)
    
 2. Install the plugin from the repository.
 
 3. Set path to SwiftLint binary in the Preferences:

    <img src="img/preferences.png" width="897" alt="SwiftLint settings in AppCode Preferences"/>

 4. Check that the corresponding inspection is turned on in AppCode:
 
     <img src="img/inspections.png" width="900" alt="Section Inspections of AppCode Preferences"/>

You are good to go!

## Development

This is a DevKit plugin. And it is old, so be prepared. 

To do something with it, you need:
 - JDK 11 (I use OpenJDK)
 - IntelliJ Idea with "Plugin DevKit" plugin enabled
 - These sources
 - You will need to set up project SDK (as IntelliJ SDK) correctly:
   - File -> Project Structure -> SDKs -> "+" button
   - Add IntelliJ Platform Plugin SDK...
   - Select "AppCode.app" (needed version)
   - Now you need to add some plugins from AppCode so that code can compile. They all are inside `AppCode.app/contents/plugins` folder. 
     Add them on `Classpath` tab of SDK configuration page.
     - cidr-cocoa-plugin/lib/cidr-cocoa-plugin.jar
     - c-plugin/lib/c-plugin.jar
     - cidr-base-plugin/lib/cidr-base-plugin.jar
     - swift-plugin/lib/swift-plugin.jar
   - (You can also configure other things like Sourcepath and Documentation paths if you want, they are not requried)
 - Select created SDK in "Project Structure -> Project -> SDK"
 - Press OK button in Project Structure window
 - After that plugin should compile and run.
 - To pack it, you can use "Build/Prepare Plugin Module 'SwiftLint' For Deployment" menu. zip will appear in `.idea` folder, 
   because it appears where .iml file is, and I put it into .idea folder.
## SwiftLint for AppCode

This simple plugin allows to highlight errors and warnings from [SwiftLint](https://github.com/realm/SwiftLint) like this:

<img src="img/swiftlint.png" width="863"/>

### Installation

You should already have SwiftLint installed somewhere.
 1. Configure SwiftLint via `.swiftlint.yml` file, if needed. Look [here](https://github.com/realm/SwiftLint#configuration) for details.
 
    (Note that the plugin works with `reporter: "csv"`)
    
 2. Install the plugin from the repository.
 
 3. Set path to SwiftLint binary in the Preferences:

    <img src="img/preferences.png" width="897" alt="SwiftLint settings in AppCode Preferences" width="677"/>

 4. Check that the corresponding inspection is turned on in AppCode:
 
     <img src="img/inspections.png" width="900" alt="Section Inspections of AppCode Preferences" width="919"/>

You are good to go!

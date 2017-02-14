##SwiftLint for AppCode

This simple plugin allows to highlight errors and warnings from SwiftLint (https://github.com/realm/SwiftLint) like this:

![](http://d.pr/i/goBy/3VsjzzWL+)


###Installation

You should already have SwiftLint installed somewhere.
 1. Configure SwiftLint via `.swiftlint.yml` file, if needed. See [here](https://github.com/realm/SwiftLint#configuration) fir details.
 
    Note that you have to set `reporter: "xcode"`.
    
 2. Install the plugin from the repository.
 
 3. Set path to SwiftLint binary in the Preferences:

    ![SwiftLint settings in AppCode Preferences](http://d.pr/i/FieQ/35oXUPSZ+)
    
 4. Adapt AppCode's inspection settings to your liking:
 
    ![Section Inspections of AppCode Preferences](http://i.imgur.com/4l8trvc.png)

You are good to go!

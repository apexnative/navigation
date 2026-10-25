# Changelog

## [1.0.0-alpha.6](https://github.com/apexnative/navigation/compare/v1.0.0-alpha.5...v1.0.0-alpha.6) (2026-10-25)


### ⚠ BREAKING CHANGES

* leave the bottom edge of a screen free by default ([#49](https://github.com/apexnative/navigation/issues/49))

### Features

* a sheet's back preview on android, lighter tabs and top bar, the compatibility mode on ios 27 ([#46](https://github.com/apexnative/navigation/issues/46)) ([24d333d](https://github.com/apexnative/navigation/commit/24d333dd43fc5e056786f22dc8d7f1a0ff82a1ad))
* leave the bottom edge of a screen free by default ([#49](https://github.com/apexnative/navigation/issues/49)) ([b6bb165](https://github.com/apexnative/navigation/commit/b6bb165152114fb2886bd3c4ee55560c418f0cbe))
* **tabs:** choose whether a tab switch animates with bottomTabs.switchAnimation ([#52](https://github.com/apexnative/navigation/issues/52)) ([c6185b6](https://github.com/apexnative/navigation/commit/c6185b6b42574cff671def1ebdf8aa4f5e4be681))


### Bug Fixes

* an explicit top edge stays free under a large title ([#53](https://github.com/apexnative/navigation/issues/53)) ([188a89e](https://github.com/apexnative/navigation/commit/188a89e8d160c071f91cc010ed43ccc0683e12b3))
* shared elements fly turned and scaled views the same way on ios and android ([#50](https://github.com/apexnative/navigation/issues/50)) ([bf63ab6](https://github.com/apexnative/navigation/commit/bf63ab624d33ff9295af297ce6506206b1bfa0e6))
* the custom tab row moves with its tabs on android, and tab cells get selected and badge ([#51](https://github.com/apexnative/navigation/issues/51)) ([fd8f41d](https://github.com/apexnative/navigation/commit/fd8f41d2efd108741672220c54253a86704929a5))


### Performance Improvements

* **ios:** render a preview's screen when a press on its view starts ([#48](https://github.com/apexnative/navigation/issues/48)) ([4a57d1a](https://github.com/apexnative/navigation/commit/4a57d1aee1df7690dbde4c7d08dca2a70773d1f5))

## [1.0.0-alpha.5](https://github.com/apexnative/navigation/compare/v1.0.0-alpha.4...v1.0.0-alpha.5) (2026-10-23)


### Features

* android back options, a launch screen copy from UILaunchScreen, modal and back fixes ([#43](https://github.com/apexnative/navigation/issues/43)) ([29f311e](https://github.com/apexnative/navigation/commit/29f311e318134f284a0c6279e6c86e15612c9343))


### Bug Fixes

* **android:** send the state of a tab switch from code at its start ([#44](https://github.com/apexnative/navigation/issues/44)) ([34d03d0](https://github.com/apexnative/navigation/commit/34d03d0214cf09e14329d0bae51e47d46063f054))
* **hooks:** measure agent folder scopes from the worktree a file is in ([#42](https://github.com/apexnative/navigation/issues/42)) ([d306137](https://github.com/apexnative/navigation/commit/d306137534d3ba551fab8a7346c7ad7300c0faf7))

## [1.0.0-alpha.4](https://github.com/apexnative/navigation/compare/v1.0.0-alpha.3...v1.0.0-alpha.4) (2026-10-23)


### Bug Fixes

* **ci:** keep a closed React Native issue instead of opening a second one ([#37](https://github.com/apexnative/navigation/issues/37)) ([242030c](https://github.com/apexnative/navigation/commit/242030c8559a6fe186f44781ada85d09970c8318))
* **ios:** lay a waiting screen out in its place, so the large title comes in with it ([#41](https://github.com/apexnative/navigation/issues/41)) ([85bfaa8](https://github.com/apexnative/navigation/commit/85bfaa877d3775264c1ba1114942cf875f890f29))
* one native contract, options as layers and pushes that wait for the first render ([#39](https://github.com/apexnative/navigation/issues/39)) ([888f21e](https://github.com/apexnative/navigation/commit/888f21eb2b3e1aec413413bbdab9546a7a8f5a6b))

## [1.0.0-alpha.3](https://github.com/apexnative/navigation/compare/v1.0.0-alpha.2...v1.0.0-alpha.3) (2026-10-22)


### Features

* **android:** system stack transitions, an opaque default screen background and predictive back ([#26](https://github.com/apexnative/navigation/issues/26)) ([9f28969](https://github.com/apexnative/navigation/commit/9f28969a30a8902c1512cd2a24c3ddf5439f4642))


### Bug Fixes

* **android:** center the back button and the bar buttons in the row ([#35](https://github.com/apexnative/navigation/issues/35)) ([c908971](https://github.com/apexnative/navigation/commit/c9089716ea4e3b3f4bcdc42b16e57134018d76c0))
* **android:** fade through on a tab switch, a new tab's first frame in place ([#29](https://github.com/apexnative/navigation/issues/29)) ([60d69b6](https://github.com/apexnative/navigation/commit/60d69b618a97c8f7da974fbdf6f427461960d672))
* **android:** let an overlay cover the status bar ([#27](https://github.com/apexnative/navigation/issues/27)) ([2632194](https://github.com/apexnative/navigation/commit/26321945cf0c457780b3eb7bba1f3d988dad4d96))
* **android:** match each modal presentation style to its Android equivalent ([#31](https://github.com/apexnative/navigation/issues/31)) ([54ab265](https://github.com/apexnative/navigation/commit/54ab2651f3139e869a2d99a43465b33bfb171414))
* **android:** move the top bar with its screen in transitions of their own ([#34](https://github.com/apexnative/navigation/issues/34)) ([4d12ee6](https://github.com/apexnative/navigation/commit/4d12ee6d6ec26a04e529bdef8b8111d20e5116aa))
* close the side menu as a pick in it changes the center ([#28](https://github.com/apexnative/navigation/issues/28)) ([c4b3e81](https://github.com/apexnative/navigation/commit/c4b3e81ba44d2edb6d93d3ddc906eca165d46e29))


### Performance Improvements

* **android:** render the modals of a setRoot's layout together with its root ([#32](https://github.com/apexnative/navigation/issues/32)) ([cf3e1d9](https://github.com/apexnative/navigation/commit/cf3e1d98ff7c5d2a05aba6485203e6fe35a3423d))

## [1.0.0-alpha.2](https://github.com/apexnative/navigation/compare/v1.0.0-alpha.1...v1.0.0-alpha.2) (2026-10-22)


### Bug Fixes

* **android:** fractional option sizes, sheet stops, live side menu size and menu appear events ([#18](https://github.com/apexnative/navigation/issues/18)) ([3ed8913](https://github.com/apexnative/navigation/commit/3ed8913126d83f5d75a265d22e3f80e581d329b8))
* **cli:** set up and make apps on React Native 0.88's template ([#23](https://github.com/apexnative/navigation/issues/23)) ([3c24f04](https://github.com/apexnative/navigation/commit/3c24f042f4d6a6a2660768a7443cc5eeba004235))
* **ios:** hand sheet detents to UIKit sorted by height and without repeats ([#15](https://github.com/apexnative/navigation/issues/15)) ([d1c1004](https://github.com/apexnative/navigation/commit/d1c10043353f45b452e7200d2c179515b6989737))
* **ios:** hide the dismissal of the old root's modals when a new root shows without animation ([#14](https://github.com/apexnative/navigation/issues/14)) ([b639232](https://github.com/apexnative/navigation/commit/b639232e8b73c38bf8599b7bac24ecc5c7104ca6))
* **ios:** keep deep links working on React Native 0.88 in apps with a scene delegate ([#24](https://github.com/apexnative/navigation/issues/24)) ([7f690cd](https://github.com/apexnative/navigation/commit/7f690cdac41be774b55cf7186105faa605249bc2))
* **ios:** keep the status bar blur in the status bar's frame as it changes ([#17](https://github.com/apexnative/navigation/issues/17)) ([d5051e9](https://github.com/apexnative/navigation/commit/d5051e9c30fc0040e726d12ef352da7cc43f2a8c))
* **ios:** log instead of calling a command's nil promise block ([#22](https://github.com/apexnative/navigation/issues/22)) ([b2ed4ca](https://github.com/apexnative/navigation/commit/b2ed4ca2cb76207b24747e78bf702ef1ab02146b))
* **ios:** reject a command whose answer goes away unanswered ([#12](https://github.com/apexnative/navigation/issues/12)) ([0914c03](https://github.com/apexnative/navigation/commit/0914c03ec465ae8273d736d1b12e5faef2ba4a52))
* **ios:** show a partialCurl modal as coverVertical when it isn't full screen ([#16](https://github.com/apexnative/navigation/issues/16)) ([404b3be](https://github.com/apexnative/navigation/commit/404b3be98beac73069bfc16bb3903f583aa9b844))
* keep a transition running when one element fails, and find its elements in one walk ([#21](https://github.com/apexnative/navigation/issues/21)) ([5dd1179](https://github.com/apexnative/navigation/commit/5dd1179834211abc985ffffbd2bd9622530be1dc))

## [1.0.0-alpha.1](https://github.com/apexnative/navigation/compare/v1.0.0-alpha.0...v1.0.0-alpha.1) (2026-10-21)


### Features

* native navigation for React Native on iOS, iPadOS and Android ([ede23be](https://github.com/apexnative/navigation/commit/ede23beebc4a09eb20cf774a90a1bca99f07923e))

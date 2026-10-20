# Changelog

## [0.2.0](https://github.com/MeSeeFoo/argus-quant-engine/compare/v0.1.0...v0.2.0) (2026-10-20)


### ⚠ BREAKING CHANGES

* **contracts:** the Signal message (SBE template 10, topic argus.signals.v1) and the Direction enum are removed. No consumer used them yet.

### Features

* add the architecture reviewer subagent ([#52](https://github.com/MeSeeFoo/argus-quant-engine/issues/52)) ([207ae8a](https://github.com/MeSeeFoo/argus-quant-engine/commit/207ae8a57e42254a83180c798f7986b8ad74da71))
* add the architecture reviewer subagent ([#52](https://github.com/MeSeeFoo/argus-quant-engine/issues/52)) ([408fa33](https://github.com/MeSeeFoo/argus-quant-engine/commit/408fa337addaf0468ebca828526a7f48687c8755))
* **contracts:** replace Signal with IndicatorValue and Alert ([#45](https://github.com/MeSeeFoo/argus-quant-engine/issues/45)) ([ed079ca](https://github.com/MeSeeFoo/argus-quant-engine/commit/ed079ca0c2d24d8032000399e105d937ad284d10))
* **feed-gateway:** map Binance market data to canonical values ([#68](https://github.com/MeSeeFoo/argus-quant-engine/issues/68)) ([f8ff0ce](https://github.com/MeSeeFoo/argus-quant-engine/commit/f8ff0ce859f7ab03dc73484aa9dae7a2184de95b))
* **feed-gateway:** map Binance market data to canonical values ([#68](https://github.com/MeSeeFoo/argus-quant-engine/issues/68)) ([cef0ad9](https://github.com/MeSeeFoo/argus-quant-engine/commit/cef0ad919d2329d86f72c78b22d9f864b2fd18b5))
* **feed-gateway:** publish canonical market data over Aeron ([#69](https://github.com/MeSeeFoo/argus-quant-engine/issues/69)) ([c2571f6](https://github.com/MeSeeFoo/argus-quant-engine/commit/c2571f6f304384aec161ccac42b1ca2e6610353c))
* **feed-gateway:** publish canonical market data over Aeron ([#69](https://github.com/MeSeeFoo/argus-quant-engine/issues/69)) ([9c8f7f8](https://github.com/MeSeeFoo/argus-quant-engine/commit/9c8f7f8c9f613e21e3227c08a8e049d43e883d9d))
* **feed-gateway:** scaffold the feed gateway service ([#67](https://github.com/MeSeeFoo/argus-quant-engine/issues/67)) ([370b25b](https://github.com/MeSeeFoo/argus-quant-engine/commit/370b25b1b8f46bd76b26fa1b7ecbebd6e18c2d9c))
* **feed-gateway:** scaffold the feed gateway service ([#67](https://github.com/MeSeeFoo/argus-quant-engine/issues/67)) ([0fd6581](https://github.com/MeSeeFoo/argus-quant-engine/commit/0fd65819e29b964069e391951304dfb61f622540))
* **infra:** provision the Kafka topics of the revised architecture ([#46](https://github.com/MeSeeFoo/argus-quant-engine/issues/46)) ([ef787bd](https://github.com/MeSeeFoo/argus-quant-engine/commit/ef787bd30e4d808aa0134a6f93768a24f6cd6787))


### Bug fixes

* **feed-gateway:** check Binance frame lengths before decoding ([#68](https://github.com/MeSeeFoo/argus-quant-engine/issues/68)) ([1c5aafd](https://github.com/MeSeeFoo/argus-quant-engine/commit/1c5aafd122eb61d9efff9be1334417eb4a4fae38))
* **feed-gateway:** retry term rotations and schedule heartbeats on a monotonic clock ([#69](https://github.com/MeSeeFoo/argus-quant-engine/issues/69)) ([23d6d81](https://github.com/MeSeeFoo/argus-quant-engine/commit/23d6d81ceca84e0cb1318c019758bd3c7fe04965))


### Documentation

* add a story issue template ([#63](https://github.com/MeSeeFoo/argus-quant-engine/issues/63)) ([964de1a](https://github.com/MeSeeFoo/argus-quant-engine/commit/964de1ac53eed61c05a98e636bf1dba1253adea7))
* add issue and pull request templates ([#10](https://github.com/MeSeeFoo/argus-quant-engine/issues/10)) ([e1f3578](https://github.com/MeSeeFoo/argus-quant-engine/commit/e1f35782614718eab8aee904dd84be368c66ecc8))
* add project instructions for Claude Code ([#49](https://github.com/MeSeeFoo/argus-quant-engine/issues/49)) ([7861f65](https://github.com/MeSeeFoo/argus-quant-engine/commit/7861f6537e8ca047ddd985f2bcf4ea897c185914))
* add project instructions for Claude Code ([#49](https://github.com/MeSeeFoo/argus-quant-engine/issues/49)) ([dc437f9](https://github.com/MeSeeFoo/argus-quant-engine/commit/dc437f9fc1476bf72908caac662ac24c057bb1da))
* add repository conventions, templates and contributing guide ([#10](https://github.com/MeSeeFoo/argus-quant-engine/issues/10)) ([a512b41](https://github.com/MeSeeFoo/argus-quant-engine/commit/a512b412f4141ed093f056a1e602b93a40cbc116))
* add the contributing guide and explain the trunk-based branching model ([#10](https://github.com/MeSeeFoo/argus-quant-engine/issues/10)) ([29c9a67](https://github.com/MeSeeFoo/argus-quant-engine/commit/29c9a67dea13ae2613654cc37ada6825490a2b5e))
* **adr:** add the ADR template, index and process ([#11](https://github.com/MeSeeFoo/argus-quant-engine/issues/11)) ([77f5954](https://github.com/MeSeeFoo/argus-quant-engine/commit/77f59546874825254f0f5ec78d44fe956411e5d3))
* **adr:** avoid a placeholder link in the ADR template ([#11](https://github.com/MeSeeFoo/argus-quant-engine/issues/11)) ([28c6729](https://github.com/MeSeeFoo/argus-quant-engine/commit/28c67292ecdf6be7c0daa708a3b9436656e99d8b))
* **adr:** document model promotion and the MiFID II regulatory archive ([#44](https://github.com/MeSeeFoo/argus-quant-engine/issues/44)) ([c6f4525](https://github.com/MeSeeFoo/argus-quant-engine/commit/c6f45250c337d280fab21c4d5199bf56813bee7a))
* **adr:** record the decisions taken before the architecture review ([#11](https://github.com/MeSeeFoo/argus-quant-engine/issues/11)) ([3c46591](https://github.com/MeSeeFoo/argus-quant-engine/commit/3c46591139d80b75089017a190c8377a77dc3dac))
* **adr:** record the market data and platform decisions of the architecture review ([#43](https://github.com/MeSeeFoo/argus-quant-engine/issues/43)) ([f71f47f](https://github.com/MeSeeFoo/argus-quant-engine/commit/f71f47f58b6ead6db672fd47256ae4ccefc991ed))
* **adr:** record the model engine decisions of the architecture review ([#42](https://github.com/MeSeeFoo/argus-quant-engine/issues/42)) ([45a028c](https://github.com/MeSeeFoo/argus-quant-engine/commit/45a028c16bd55b52f3d40f4ea7b37a037d674528))
* **contracts:** define a heartbeat sequence of 0 ([#69](https://github.com/MeSeeFoo/argus-quant-engine/issues/69)) ([25d4224](https://github.com/MeSeeFoo/argus-quant-engine/commit/25d4224ea8819d8d4c5732483c1f5db7a4d87e69))
* **contracts:** describe IndicatorValue, Alert and how to retire a message ([#45](https://github.com/MeSeeFoo/argus-quant-engine/issues/45)) ([297d2e3](https://github.com/MeSeeFoo/argus-quant-engine/commit/297d2e3cea0e0e07a3a677c7b6ee2af56413e27c))
* describe how to invoke the reviewer agent ([#52](https://github.com/MeSeeFoo/argus-quant-engine/issues/52)) ([9b7bfd1](https://github.com/MeSeeFoo/argus-quant-engine/commit/9b7bfd192a06f9dc220d3d2726cdb45769430359))
* describe the Claude Code settings and hooks ([#50](https://github.com/MeSeeFoo/argus-quant-engine/issues/50)) ([3d5d56c](https://github.com/MeSeeFoo/argus-quant-engine/commit/3d5d56c08926c06114d9d8808b99a689daae72d8))
* describe the revised architecture in the README and the roadmap ([#41](https://github.com/MeSeeFoo/argus-quant-engine/issues/41)) ([dac1d7f](https://github.com/MeSeeFoo/argus-quant-engine/commit/dac1d7fa154606b5fb97783452f95d9d7f059f3f))
* link the ADRs from the contributing guide and check docs/adr links again ([#11](https://github.com/MeSeeFoo/argus-quant-engine/issues/11)) ([0d3f18f](https://github.com/MeSeeFoo/argus-quant-engine/commit/0d3f18fff26f0715d5a562c140e99896d052a8c4))
* link the contributing guide from the README ([#10](https://github.com/MeSeeFoo/argus-quant-engine/issues/10)) ([1e10458](https://github.com/MeSeeFoo/argus-quant-engine/commit/1e10458277ad5bc8154ef07d304b30c4606822ce))
* list the feed gateway among the existing modules ([#67](https://github.com/MeSeeFoo/argus-quant-engine/issues/67)) ([9250df1](https://github.com/MeSeeFoo/argus-quant-engine/commit/9250df14a266301b773ef265b4d8913487f3eb80))
* record the architecture decisions and close the architecture review ([#11](https://github.com/MeSeeFoo/argus-quant-engine/issues/11)) ([ed05db3](https://github.com/MeSeeFoo/argus-quant-engine/commit/ed05db3127b5076a5491a6995e7c1010b5fed90d))
* tick phase 0 in the README roadmap ([#49](https://github.com/MeSeeFoo/argus-quant-engine/issues/49)) ([59453e1](https://github.com/MeSeeFoo/argus-quant-engine/commit/59453e106e02fb9806bdbc81190e9abb145db8c0))
* track work as epics, stories and tasks ([#63](https://github.com/MeSeeFoo/argus-quant-engine/issues/63)) ([cf9934b](https://github.com/MeSeeFoo/argus-quant-engine/commit/cf9934bd182971bc6da0795c65291058a534b1fc))
* track work as epics, stories and tasks ([#63](https://github.com/MeSeeFoo/argus-quant-engine/issues/63)) ([3c2b5d9](https://github.com/MeSeeFoo/argus-quant-engine/commit/3c2b5d96892fba84c38634c4db0fb1b4cb7de7aa))


### Build

* **deps:** bump sbe-tool to 1.41.0 ([#68](https://github.com/MeSeeFoo/argus-quant-engine/issues/68)) ([b82b44a](https://github.com/MeSeeFoo/argus-quant-engine/commit/b82b44a12bfe033259dabefe31baf1fea7bc4240))
* **deps:** bump the maven-minor-and-patch group across 1 directory with 2 updates ([e62cd3b](https://github.com/MeSeeFoo/argus-quant-engine/commit/e62cd3b3fe455ea2a958f5f572f01f71b409ed07))
* **deps:** bump the maven-minor-and-patch group across 1 directory with 2 updates ([256b569](https://github.com/MeSeeFoo/argus-quant-engine/commit/256b56913405e5f9ccb6b57337128d094cf53889))
* **feed-gateway:** generate Binance's SBE stream codecs ([#68](https://github.com/MeSeeFoo/argus-quant-engine/issues/68)) ([d410547](https://github.com/MeSeeFoo/argus-quant-engine/commit/d410547487ae939f062e291d115d979d7351956d))
* manage the argus-contracts version ([#69](https://github.com/MeSeeFoo/argus-quant-engine/issues/69)) ([e40e95d](https://github.com/MeSeeFoo/argus-quant-engine/commit/e40e95dd91cc862ab78c9a8e90ba8f965ee6e0fd))
* manage the maven-jar-plugin version ([#67](https://github.com/MeSeeFoo/argus-quant-engine/issues/67)) ([8dd3b9b](https://github.com/MeSeeFoo/argus-quant-engine/commit/8dd3b9b16f9e4faf23d00853077796e3a98ea4ab))
* store mvnw.cmd with LF line endings again ([#32](https://github.com/MeSeeFoo/argus-quant-engine/issues/32)) ([489baca](https://github.com/MeSeeFoo/argus-quant-engine/commit/489baca39b14c2de3b827c04785dd95eb7a8c86a))


### Continuous integration

* check that files keep the line endings set in .gitattributes ([#37](https://github.com/MeSeeFoo/argus-quant-engine/issues/37)) ([ef00d65](https://github.com/MeSeeFoo/argus-quant-engine/commit/ef00d6574d8ac94a3d4ebe47bab03df44baec62b))
* check that files keep the line endings set in .gitattributes ([#37](https://github.com/MeSeeFoo/argus-quant-engine/issues/37)) ([71cb534](https://github.com/MeSeeFoo/argus-quant-engine/commit/71cb534e6f66468ca333a78ad8fe6921c0a4372e))
* reject force-pushes on every branch ([#59](https://github.com/MeSeeFoo/argus-quant-engine/issues/59)) ([c8c7233](https://github.com/MeSeeFoo/argus-quant-engine/commit/c8c723324db76be1f9961224aae9f4b5783291a0))
* reject force-pushes on every branch ([#59](https://github.com/MeSeeFoo/argus-quant-engine/issues/59)) ([9b5373e](https://github.com/MeSeeFoo/argus-quant-engine/commit/9b5373e6643bdab15c4a797624a880cd10278458))
* require the line endings check on main ([#37](https://github.com/MeSeeFoo/argus-quant-engine/issues/37)) ([acae894](https://github.com/MeSeeFoo/argus-quant-engine/commit/acae894dede0858a3b73ff4be7fd7f5d5138467c))
* run the Claude Code hooks on Linux and Windows ([#50](https://github.com/MeSeeFoo/argus-quant-engine/issues/50)) ([f7eb91e](https://github.com/MeSeeFoo/argus-quant-engine/commit/f7eb91ec3de654027f6473a4c0c60f046199b870))

## 0.1.0 (2026-10-16)


### Features

* **build:** add ArchUnit rules for the hexagonal architecture ([#8](https://github.com/MeSeeFoo/argus-quant-engine/issues/8)) ([5978b48](https://github.com/MeSeeFoo/argus-quant-engine/commit/5978b4860d9eab45c993a0e78aa6447b7402ca96))
* **contracts:** add fast-path SBE schema and codec generation ([#4](https://github.com/MeSeeFoo/argus-quant-engine/issues/4)) ([a1f7d3d](https://github.com/MeSeeFoo/argus-quant-engine/commit/a1f7d3d95cb9c0392e4dff03077218762cc91587))
* **contracts:** add Kafka Protobuf schemas and code generation ([#5](https://github.com/MeSeeFoo/argus-quant-engine/issues/5)) ([6f3768e](https://github.com/MeSeeFoo/argus-quant-engine/commit/6f3768e602a5f6c4daf31d0c2b99a1c614e6ee33))
* **contracts:** Protobuf schemas for Kafka ([#5](https://github.com/MeSeeFoo/argus-quant-engine/issues/5)) ([c48e2f6](https://github.com/MeSeeFoo/argus-quant-engine/commit/c48e2f64a2c76697059f0d82c4d4fb97972edd6c))
* **contracts:** SBE schemas for the fast path ([#4](https://github.com/MeSeeFoo/argus-quant-engine/issues/4)) ([9a80fa8](https://github.com/MeSeeFoo/argus-quant-engine/commit/9a80fa838880ef8b68d3469ecb23438a4d03b37a))
* **infra:** add local Docker Compose infrastructure ([#6](https://github.com/MeSeeFoo/argus-quant-engine/issues/6)) ([09d5438](https://github.com/MeSeeFoo/argus-quant-engine/commit/09d54387d0b378dace8ca67a1fc7709e64f60168))
* **infra:** automatic Kafka topic provisioning ([#7](https://github.com/MeSeeFoo/argus-quant-engine/issues/7)) ([88ec2ee](https://github.com/MeSeeFoo/argus-quant-engine/commit/88ec2eee85dc3b7d44a3410c4d62634856923b8a))
* **infra:** local infrastructure with Docker Compose ([#6](https://github.com/MeSeeFoo/argus-quant-engine/issues/6)) ([a2cd80e](https://github.com/MeSeeFoo/argus-quant-engine/commit/a2cd80e5a687d07fa9a112f2a89e87d073f964b6))
* **infra:** provision Kafka topics from a versioned definition file ([#7](https://github.com/MeSeeFoo/argus-quant-engine/issues/7)) ([d8badf3](https://github.com/MeSeeFoo/argus-quant-engine/commit/d8badf36f09f9d2a4d14b8888a0a6496f56b6f70))


### Bug fixes

* **build:** override lz4-java to a version without known vulnerabilities ([#29](https://github.com/MeSeeFoo/argus-quant-engine/issues/29)) ([4576e63](https://github.com/MeSeeFoo/argus-quant-engine/commit/4576e6343ab09aa1038a8f74d247a6b50a025bec))
* **ci:** fix the SonarQube organisation and the first release version ([#34](https://github.com/MeSeeFoo/argus-quant-engine/issues/34)) ([5961b81](https://github.com/MeSeeFoo/argus-quant-engine/commit/5961b812bf1ae7dec8b5db299a0ec4c109d8a799))
* **ci:** run the Maven Wrapper from the current directory on Windows ([#29](https://github.com/MeSeeFoo/argus-quant-engine/issues/29)) ([91be410](https://github.com/MeSeeFoo/argus-quant-engine/commit/91be4109da2cf55d79a5667d5a819e1bce5d4a46))
* **ci:** start the release numbering at 0.1.0 ([#34](https://github.com/MeSeeFoo/argus-quant-engine/issues/34)) ([8e22b3d](https://github.com/MeSeeFoo/argus-quant-engine/commit/8e22b3d4e961826e5ce4dc4b694730c67af84ede))
* **ci:** use the real SonarQube Cloud organisation key ([#34](https://github.com/MeSeeFoo/argus-quant-engine/issues/34)) ([3ed6566](https://github.com/MeSeeFoo/argus-quant-engine/commit/3ed6566821a144229b1396a14d67593778d57ae5))


### Refactoring

* **build:** use ArchUnit's onionArchitecture for layer rules ([#8](https://github.com/MeSeeFoo/argus-quant-engine/issues/8)) ([ccb7a01](https://github.com/MeSeeFoo/argus-quant-engine/commit/ccb7a01880d568434fe3583cb12eabae8f240dd5))
* **contracts:** replace Kafka Candle with BestBidAsk ([#5](https://github.com/MeSeeFoo/argus-quant-engine/issues/5)) ([0813c4b](https://github.com/MeSeeFoo/argus-quant-engine/commit/0813c4b7d480148a735917e1214847241b778fbf))


### Documentation

* add the CI badge and describe the pipeline ([#9](https://github.com/MeSeeFoo/argus-quant-engine/issues/9)) ([122c6f9](https://github.com/MeSeeFoo/argus-quant-engine/commit/122c6f9132209b20aa41455d88622595488ae038))
* **contracts:** document fast-path messages and versioning ([#4](https://github.com/MeSeeFoo/argus-quant-engine/issues/4)) ([fc5c515](https://github.com/MeSeeFoo/argus-quant-engine/commit/fc5c515e2fe12525790e279805f5961a37a152c9))
* **contracts:** document Kafka messages, conventions and schema evolution ([#5](https://github.com/MeSeeFoo/argus-quant-engine/issues/5)) ([4150639](https://github.com/MeSeeFoo/argus-quant-engine/commit/41506395eb2c12c8a103fed3f170a6591e8e14fe))
* describe the CI pipelines, when they run and how to run them by hand ([#21](https://github.com/MeSeeFoo/argus-quant-engine/issues/21)) ([ac8beea](https://github.com/MeSeeFoo/argus-quant-engine/commit/ac8beea1a85aefd8a799f7ee6c26cf59c5b382d8))
* document how to start, stop and reset the local environment ([#6](https://github.com/MeSeeFoo/argus-quant-engine/issues/6)) ([290fb91](https://github.com/MeSeeFoo/argus-quant-engine/commit/290fb913496e5d6764bafa248ba02f269872d956))
* document Kafka topic provisioning ([#7](https://github.com/MeSeeFoo/argus-quant-engine/issues/7)) ([cdd1e6b](https://github.com/MeSeeFoo/argus-quant-engine/commit/cdd1e6ba64b47defcd97dc497529edbf67492442))
* document the Actions permission that release-please needs ([#34](https://github.com/MeSeeFoo/argus-quant-engine/issues/34)) ([c1ab217](https://github.com/MeSeeFoo/argus-quant-engine/commit/c1ab217835c43f4cf8e991548762496023c4fd3c))
* explain how new services apply the architecture rules ([#8](https://github.com/MeSeeFoo/argus-quant-engine/issues/8)) ([c3f15fa](https://github.com/MeSeeFoo/argus-quant-engine/commit/c3f15fa6ef9826552690a1bdead1c8411d211aff))
* **infra:** explain the purpose of each Kafka topic ([#7](https://github.com/MeSeeFoo/argus-quant-engine/issues/7)) ([fe7165b](https://github.com/MeSeeFoo/argus-quant-engine/commit/fe7165b0173fe8afdf338786a6f0ec1f08fc78b8))
* keep every market tick on Kafka and compute candles on request ([#5](https://github.com/MeSeeFoo/argus-quant-engine/issues/5)) ([78c9504](https://github.com/MeSeeFoo/argus-quant-engine/commit/78c9504439b2fa1572a56b7ea059b1e0105d647b))
* **readme:** explain how to build the project ([#1](https://github.com/MeSeeFoo/argus-quant-engine/issues/1)) ([4036266](https://github.com/MeSeeFoo/argus-quant-engine/commit/40362666485ee61cf1b4795222ef4c1bdbc807ce))
* remove a duplicated sentence in the hexagonal architecture section ([#9](https://github.com/MeSeeFoo/argus-quant-engine/issues/9)) ([4b0ace9](https://github.com/MeSeeFoo/argus-quant-engine/commit/4b0ace9b9278ac56d9d7b430c7deeeb779d7013a))
* revised architecture and roadmap ([e81ccc3](https://github.com/MeSeeFoo/argus-quant-engine/commit/e81ccc36add0d20bd5c95979f7326ab2aa16db2a))
* revised architecture and roadmap ([#3](https://github.com/MeSeeFoo/argus-quant-engine/issues/3)) ([e81ccc3](https://github.com/MeSeeFoo/argus-quant-engine/commit/e81ccc36add0d20bd5c95979f7326ab2aa16db2a))


### Build

* add Error Prone and NullAway to the compiler ([#23](https://github.com/MeSeeFoo/argus-quant-engine/issues/23)) ([d669644](https://github.com/MeSeeFoo/argus-quant-engine/commit/d669644765bb6c2e0a882bd342e0cc6127fba438))
* add Maven Wrapper pinned to Maven 3.9.16 ([#1](https://github.com/MeSeeFoo/argus-quant-engine/issues/1)) ([3325c0b](https://github.com/MeSeeFoo/argus-quant-engine/commit/3325c0b3a7fbc5657c2a53222c9f4531db6da1af))
* add parent POM with centralised versions, Enforcer and Spotless ([#1](https://github.com/MeSeeFoo/argus-quant-engine/issues/1)) ([64d9f38](https://github.com/MeSeeFoo/argus-quant-engine/commit/64d9f380d09ad952e2f62c8178a8f04d88de5c56))
* enforce coverage thresholds with JaCoCo ([#22](https://github.com/MeSeeFoo/argus-quant-engine/issues/22)) ([fcb2aa2](https://github.com/MeSeeFoo/argus-quant-engine/commit/fcb2aa2294e95b0244193df1e6a619887b774976))
* Maven multi-module build ([#1](https://github.com/MeSeeFoo/argus-quant-engine/issues/1)) ([bd70929](https://github.com/MeSeeFoo/argus-quant-engine/commit/bd70929b5a5764f08af5b5d43cc9593642b9f244))
* rename groupId to com.argus ([#1](https://github.com/MeSeeFoo/argus-quant-engine/issues/1)) ([10558a7](https://github.com/MeSeeFoo/argus-quant-engine/commit/10558a7cbeb14a52e6cc7b41d461cf3ce6068c45))


### Continuous integration

* add a nightly pipeline for Windows, external links and vulnerabilities ([#29](https://github.com/MeSeeFoo/argus-quant-engine/issues/29)) ([ff68633](https://github.com/MeSeeFoo/argus-quant-engine/commit/ff686335ea2589507e3193afb1ea725e3fc57214))
* add pull request checks for conventions, dependencies, links and workflows ([#25](https://github.com/MeSeeFoo/argus-quant-engine/issues/25)) ([4ff3206](https://github.com/MeSeeFoo/argus-quant-engine/commit/4ff3206923fe9afadd811dfc896d64619b2f1aea))
* add the SonarQube Cloud quality gate ([#24](https://github.com/MeSeeFoo/argus-quant-engine/issues/24)) ([37f9e88](https://github.com/MeSeeFoo/argus-quant-engine/commit/37f9e88e4d92009b82364262a5aabe32a0286848))
* automate releases with release-please and attach an SBOM ([#28](https://github.com/MeSeeFoo/argus-quant-engine/issues/28)) ([db4c0f9](https://github.com/MeSeeFoo/argus-quant-engine/commit/db4c0f9ce30867adacb63e7aa11cd3198931c03b))
* build, test and smoke-test the Compose stack on GitHub Actions ([#9](https://github.com/MeSeeFoo/argus-quant-engine/issues/9)) ([6744dfe](https://github.com/MeSeeFoo/argus-quant-engine/commit/6744dfe6ff52505982fd0dc2b5ba490b263c6540))
* detect breaking changes in the Protobuf contracts with buf ([#26](https://github.com/MeSeeFoo/argus-quant-engine/issues/26)) ([a45ddde](https://github.com/MeSeeFoo/argus-quant-engine/commit/a45dddefdb41db366d485315effe12c85d790a46))
* GitHub Actions pipeline with build, test reports and Compose smoke test ([#9](https://github.com/MeSeeFoo/argus-quant-engine/issues/9)) ([487f1dc](https://github.com/MeSeeFoo/argus-quant-engine/commit/487f1dc54fe39b377ec1a3547d73f7a2b9ad0002))
* pin every action to a commit SHA and add Dependabot ([#27](https://github.com/MeSeeFoo/argus-quant-engine/issues/27)) ([2f8e7fd](https://github.com/MeSeeFoo/argus-quant-engine/commit/2f8e7fda1dc04672cad9ba3bcee0e0b30498dad3))
* protect main with a ruleset that requires the CI checks ([#30](https://github.com/MeSeeFoo/argus-quant-engine/issues/30)) ([4e77891](https://github.com/MeSeeFoo/argus-quant-engine/commit/4e778910e81c1491e75f58e8cc8737944bbc04e0))
* quality gates, security checks, releases and nightly pipeline ([#9](https://github.com/MeSeeFoo/argus-quant-engine/issues/9)) ([dbb1469](https://github.com/MeSeeFoo/argus-quant-engine/commit/dbb1469af7758fe10f60ac22ec4fa57ab7086f09))

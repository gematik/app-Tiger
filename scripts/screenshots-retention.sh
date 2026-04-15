#!/bin/bash
#
# Copyright 2026 gematik GmbH
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# *******
#
# For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
#
# screenshots-retention.sh — Thin wrapper that runs ScreenshotPipeline retain.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec mvn -f "$SCRIPT_DIR/../tiger-uitests/pom.xml" -ntp -q exec:java \
  -Dexec.mainClass=de.gematik.test.tiger.screenshots.ScreenshotPipeline \
  -Dexec.args="retain $*" \
  -Dlicense.skip=true


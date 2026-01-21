///
///
/// Copyright 2021-2026 gematik GmbH
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///
/// *******
///
/// For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
///

export const setupDragDetector = (
  element: HTMLElement,
  onDrag: (ev: MouseEvent) => any,
) => {
  let drag = false;
  const delta = 6;
  let startX: number;
  let startY: number;
  let diffX: number = 0;
  let diffY: number = 0;

  function isDrag(): boolean {
    return drag && (diffX > delta || diffY > delta);
  }

  const onMouseMove = (e: MouseEvent) => {
    drag = true;
    diffX = Math.abs(e.pageX - startX);
    diffY = Math.abs(e.pageY - startY);
    if (isDrag()) {
      onDrag(e);
    }
  };

  const onMouseUp = (_e: MouseEvent) => {
    document.removeEventListener("mousemove", onMouseMove);
    document.removeEventListener("mouseup", onMouseUp);

    drag = false;
  };

  element.addEventListener("mousedown", (e) => {
    startX = e.pageX;
    startY = e.pageY;
    drag = false;
    document.addEventListener("mousemove", onMouseMove);
    document.addEventListener("mouseup", onMouseUp);
  });
};

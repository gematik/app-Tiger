///
///
/// Copyright 2021-2025 gematik GmbH
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

export default class Ui {
  private rbelLogDetailsResizer: HTMLElement | undefined;
  private mainContentElement: HTMLElement | undefined;
  private executionTable: HTMLElement | undefined;
  private rbelLogDetailsPane: HTMLElement | undefined;
  private readonly mouseMoveListener: (ev: MouseEvent) => void;
  private readonly mouseUpListener: (ev: MouseEvent) => void;

  constructor() {
    this.mouseMoveListener = (ev) => this.mouseMoveHandler(ev);
    this.mouseUpListener = () => this.mouseUpHandler();
  }

  public init() {
    if (!this.rbelLogDetailsResizer) {
      this.rbelLogDetailsResizer = document.getElementById(
        "rbellog_resize",
      ) as HTMLElement;
      this.mainContentElement = document.getElementById(
        "main-content",
      ) as HTMLElement;
      this.executionTable = document.getElementById(
        "execution_table",
      ) as HTMLElement;
      this.rbelLogDetailsPane = document.getElementById(
        "rbellog_details_pane",
      ) as HTMLElement;
    }
  }

  public toggleRightSideBar(event: MouseEvent) {
    this.init();
    event.preventDefault();
    if (this.rbelLogDetailsPane?.classList.contains("d-none")) {
      this.rbelLogDetailsPane?.classList.toggle("d-none", true);
      this.resizeBasedOn(this.mainContentElement!.clientWidth / 2, true);
    } else {
      this.minimizeRBelLogDetailsPane();
    }
    this.toggleRightSideBarIcon();
    this.mouseLeaveHandler();
  }

  public toggleRightSideBarIcon() {
    const icon: HTMLElement = document.getElementsByClassName(
      "resizer-right",
    )[0] as HTMLElement;
    if (this.rbelLogDetailsPane?.classList.contains("d-none")) {
      Ui.replaceCssClass(icon, "fa-angles-right", "fa-angles-left");
    } else {
      Ui.replaceCssClass(icon, "fa-angles-left", "fa-angles-right");
    }
  }

  private static replaceCssClass(
    elem: HTMLElement,
    search: string,
    replacement: string,
  ): void {
    if (!elem) return;
    const css = elem.getAttribute("class");
    if (css) {
      elem.setAttribute("class", css.replace(search, replacement));
    }
  }

  public showRbelLogDetails(rbelMessageUuid: string, event: MouseEvent) {
    this.init();
    event.preventDefault();

    if (
      this.rbelLogDetailsPane?.getAttribute("class")?.indexOf("d-none") !== -1
    ) {
      this.resizeBasedOn(this.mainContentElement!.clientWidth / 2, true);
      this.mouseUpHandler();
    }
    const embeddedLog = document.querySelector("#rbellog-details-iframe") as
      | HTMLIFrameElement
      | undefined;
    if (embeddedLog) {
      const basePath = embeddedLog.src.split("#").at(0) ?? embeddedLog.src;
      embeddedLog.src = `${basePath}#${rbelMessageUuid}`;
    }

    return false;
  }

  public resizeMouseX = -1;
  public resizeActive = false;

  // Handle the mousedown event
  // that's triggered when user drags the resizer
  public mouseDownHandler(e: MouseEvent) {
    this.init();
    this.resizeMouseX = this.rbelLogDetailsPane!.clientWidth + e.clientX;
    this.resizeActive = true;

    this.activateDragMode();
    document.addEventListener("mousemove", this.mouseMoveListener);
    document.addEventListener("mouseup", this.mouseUpListener);
  }

  private activateDragMode() {
    document.body.style.cursor = "col-resize";
    document.body.style.userSelect = "none";
    this.executionTable!.style.userSelect = "none";
    this.executionTable!.style.pointerEvents = "none";
    this.rbelLogDetailsPane!.style.userSelect = "none";
    this.rbelLogDetailsPane!.style.pointerEvents = "none";
  }

  public mouseMoveHandler(e: MouseEvent) {
    this.activateDragMode();
    if (this.resizeActive) {
      this.resizeBasedOn(this.resizeMouseX - e.clientX, false);
    }
  }

  public resizeBasedOn(dx: number, alwaysShow: boolean) {
    const rbelDetailsWidth = Math.abs(dx);
    const widthStr = rbelDetailsWidth + "px";
    this.rbelLogDetailsPane!.style.width = widthStr;
    this.rbelLogDetailsPane!.style.right = "0";
    this.rbelLogDetailsPane!.classList.toggle(
      "d-none",
      !alwaysShow && rbelDetailsWidth < 300,
    );
    this.rbelLogDetailsResizer!.style.width = "10px";
    this.rbelLogDetailsResizer!.style.right = widthStr;
    this.toggleRightSideBarIcon();
  }

  public mouseUpHandler() {
    this.resizeActive = false;
    if (this.rbelLogDetailsPane!.classList.contains("d-none")) {
      this.minimizeRBelLogDetailsPane();
    }
    document.removeEventListener("mousemove", this.mouseMoveListener);
    document.removeEventListener("mouseup", this.mouseUpListener);
    this.mouseLeaveHandler();
  }

  private minimizeRBelLogDetailsPane() {
    this.rbelLogDetailsPane?.classList.toggle("d-none", true);
    this.rbelLogDetailsResizer!.style.width = "16px";
    this.rbelLogDetailsResizer!.style.right = "2px";
  }

  public mouseEnterHandler() {
    this.init();
    this.rbelLogDetailsResizer!.style.cursor = "col-resize";
  }

  public mouseLeaveHandler() {
    this.init();
    document.body.style.removeProperty("cursor");
    document.body.style.removeProperty("user-select");
    this.rbelLogDetailsResizer?.style.removeProperty("cursor");
    this.rbelLogDetailsPane?.style.removeProperty("user-select");
    this.rbelLogDetailsPane?.style.removeProperty("pointer-events");
    this.executionTable?.style.removeProperty("user-select");
    this.executionTable?.style.removeProperty("pointer-events");
  }
}

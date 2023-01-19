/*
 * Copyright (c) 2023 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

export default class Ui {

  private rbelLogDetailsResizer: HTMLElement | undefined;
  private executionTable: HTMLElement | undefined;
  private executionPanel: HTMLElement | undefined;
  private rbelLogDetailsPane: HTMLElement | undefined;
  private iframe : HTMLIFrameElement | undefined;
  private readonly mouseMoveListener: (ev: MouseEvent) => void;
  private readonly mouseUpListener: (ev: MouseEvent) => void;

  constructor() {
    this.mouseMoveListener = ev => this.mouseMoveHandler(ev);
    this.mouseUpListener = ev => this.mouseUpHandler();
  }

  public init() {
    if (!this.rbelLogDetailsResizer) {
      this.iframe = document.getElementById("rbellog-details-iframe") as HTMLIFrameElement;
      this.rbelLogDetailsResizer = document.getElementById('rbellog_resize') as HTMLElement;
      this.executionTable = document.getElementById('execution_table') as HTMLElement;
      this.executionPanel = this.executionTable.parentElement as HTMLElement;
      this.rbelLogDetailsPane = document.getElementById('rbellog_details_pane') as HTMLElement;
    }
  }

  public static toggleLeftSideBar(open: number) {
    const sidebar: HTMLDivElement = document.getElementById("sidebar-left") as HTMLDivElement;
    const mainContent: HTMLDivElement = document.getElementById("main-content") as HTMLDivElement;
    const classes: string = sidebar.getAttribute("class") as string;
    const mainClasses: string = mainContent.getAttribute("class") as string;

    // TODO update right sidebar
    document.getElementById("execution_table")?.style.removeProperty('width');
    document.getElementById("workflow-messages")?.style.removeProperty('width');

    if (open === 0) {
      sidebar.setAttribute("class", classes.replace("col-md-3", "sidebar-collapsed"))
      mainContent.setAttribute("class", mainClasses.replace("col-md-9", "col-md-11"))
    } else {
      sidebar.setAttribute("class", classes.replace("sidebar-collapsed", "col-md-3"))
      mainContent.setAttribute("class", mainClasses.replace("col-md-11", "col-md-9"))
    }
  }


  public toggleRightSideBar(event: MouseEvent) {
    this.init();
    event.preventDefault();
    if (this.rbelLogDetailsPane!.classList.contains('d-none')) {
      this.rbelLogDetailsPane!.classList.toggle('d-none', true);
      this.resizeBasedOn(this.executionPanel!.clientWidth / 2, true);
    } else {
      this.minimizeRBelLogDetailsPane();
    }
    this.toggleRightSideBarIcon();
    this.mouseLeaveHandler();
  }

  public toggleRightSideBarIcon() {
    const icon: HTMLElement = document.getElementsByClassName('resizer-right')[0] as HTMLElement;
    if (this.rbelLogDetailsPane?.classList.contains("d-none")) {
      Ui.replaceCssClass(icon, 'fa-angles-right', 'fa-angles-left');
    } else {
      Ui.replaceCssClass(icon, 'fa-angles-left', 'fa-angles-right');
    }
  }

  private static replaceCssClass(elem: HTMLElement, search: string, replacement: string): void {
    if (!elem) return;
    const css = elem.getAttribute('class');
    if (css) {
      elem.setAttribute('class', css.replace(search, replacement));
    }
  }

  public showRbelLogDetails(rbelMessageUuid: string, event: MouseEvent) {
    this.init();
    event.preventDefault();

    if (this.rbelLogDetailsPane!.getAttribute("class")?.indexOf("d-none") !== -1) {
      this.resizeBasedOn(this.executionTable!.clientWidth / 2, true);
      this.mouseUpHandler();
    }
    // firefox does not navigate to the uuid if the iframe is hidden :(
    window.setTimeout(() => {
      this.iframe!.src = this.iframe!.src.split("#")[0] + "#" + rbelMessageUuid;
    }, 100);
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
    // this.rbelLogDetailsResizer!.style.cursor = 'col-resize';
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
    this.executionTable!.style.userSelect = 'none';
    this.executionTable!.style.pointerEvents = 'none';
    this.rbelLogDetailsPane!.style.userSelect = 'none';
    this.rbelLogDetailsPane!.style.pointerEvents = 'none';
  }

  public mouseMoveHandler(e: MouseEvent) {
    this.activateDragMode();
    if (this.resizeActive) {
      const prevWidth = this.executionTable!.clientWidth;
      this.resizeBasedOn(this.resizeMouseX - e.clientX, false);
      if (this.executionTable!.clientWidth !== prevWidth) {
        // this.resizeMouseX = e.clientX
      }
    }
  }

  public resizeBasedOn(dx: number, alwaysShow: boolean) {
    const rbelDetailsWidth = Math.abs(dx);
    const widthStr = rbelDetailsWidth + 'px';
    this.rbelLogDetailsPane!.style.width = widthStr;
    this.rbelLogDetailsPane!.style.right = '0';
    this.rbelLogDetailsPane!.classList.toggle('d-none', !alwaysShow && rbelDetailsWidth < 300);
    this.rbelLogDetailsResizer!.style.width = '10px';
    this.rbelLogDetailsResizer!.style.right = widthStr;
    this.toggleRightSideBarIcon();
  }

  public mouseUpHandler() {
    this.resizeActive = false;
    if (this.rbelLogDetailsPane!.classList.contains("d-none")) {
      this.minimizeRBelLogDetailsPane();
    }
    document.removeEventListener('mousemove', this.mouseMoveListener);
    document.removeEventListener('mouseup', this.mouseUpListener);
    this.mouseLeaveHandler();
  }

  private minimizeRBelLogDetailsPane() {
    this.rbelLogDetailsPane!.classList.toggle('d-none', true);
    this.rbelLogDetailsResizer!.style.width = '16px';
    this.rbelLogDetailsResizer!.style.right = '2px';
  }

  public mouseEnterHandler() {
    this.init();
    this.rbelLogDetailsResizer!.style.cursor = 'col-resize';
  }

  public mouseLeaveHandler() {
    this.init();
    document.body.style.removeProperty("cursor");
    document.body.style.removeProperty('user-select');
    this.rbelLogDetailsResizer!.style.removeProperty("cursor");
    this.rbelLogDetailsPane!.style.removeProperty('user-select');
    this.rbelLogDetailsPane!.style.removeProperty('pointer-events');
    this.executionTable!.style.removeProperty('user-select');
    this.executionTable!.style.removeProperty('pointer-events');
  }
}

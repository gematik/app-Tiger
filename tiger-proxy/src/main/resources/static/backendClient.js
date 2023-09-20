/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

class BackendClient {
    eventTarget;
    constructor() {
        this.eventTarget = new EventTarget();
    }
    async resetMessages(){
        const response = await this.fetchWithHandler("/webui/resetMsgs");
        if (response.ok) {
            const asJson = await response.json();
            console.log("removed " + asJson.numMsgs + " messages...");
        } else {
            console.log("ERROR " + response.status + " " + response.statusText);
        }
    }

    async uploadTrafficFile(fileToUpload){
        return this.fetchWithHandler('/webui/traffic', {
            method: "POST",
            body: fileToUpload
        });
    }

    async getMsgAfter(lastMsgUuid, filterCriterion, pageSize, pageNumber){
        const baseUrl = "/webui/getMsgAfter?";
        const queryParams = new URLSearchParams({
            lastMsgUuid,
            filterCriterion});
        if( pageSize )
        {
            queryParams.append("pageSize", pageSize);
            queryParams.append("pageNumber", pageNumber);
        }
        return this.fetchWithHandler(baseUrl + queryParams.toString());
    }

    async addRoute(route){
        return this.fetchWithHandler("/route", {
            method: "PUT",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(route)
        })
    }

    async getRoutes(){
        return this.fetchWithHandler("/route");
    }

    async uploadReport(htmlReport){
        return this.fetch("webui/uploadReport", {
            method: "POST",
            body: htmlReport
        })
    }

    async testJexlQuery(msgUuid, query){
        const baseUrl = "/webui/testJexlQuery?";
        const queryParams = new URLSearchParams({
            msgUuid,
            query
        })
        return this.fetchWithHandler(baseUrl+queryParams.toString())
    }

    async testRbelExpression(msgUuid, rbelPath) {
        const baseUrl = "/webui/testRbelExpression?";
        const queryParams = new URLSearchParams({
            msgUuid,
            rbelPath
        })
        return this.fetchWithHandler(baseUrl+queryParams.toString())
    }

    async deleteRoute(routeId){
        return this.fetchWithHandler("/route"+routeId, {
            method: "DELETE"
        })
    }

    async quitProxy(noSystemExit){
        let baseUrl = "/webui/quit?"
        if(noSystemExit)
        {
            baseUrl += new URLSearchParams({noSystemExit}).toString()
        }
        //Here we don't want to throw events
        return fetch(baseUrl);
    }

    async fetchWithHandler(input, init){
        try{
            return await fetch(input, init)
        } catch (error)
        {
            this.triggerErrorEvent(error)
            throw error; //rethrowing so that calling code can do something with it if necessary.
        }
    }

    triggerErrorEvent(error) {
        const errorEvent = new CustomEvent("BackendClientErrorEvent", {detail: error})
        this.eventTarget.dispatchEvent(errorEvent);
    }

    addErrorEventListener(listener) {
        this.eventTarget.addEventListener("BackendClientErrorEvent", listener)
    }

    removeEventListener(listener) {
        this.eventTarget.removeEventListener(listener);
    }
}


export default new BackendClient();
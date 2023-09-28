/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

class SenderReceiversFrequencyCounter{
    constructor() {
        this.senders = {};
        this.receivers = {}
        this.seenMessageIds = new Set();
    }

    addSenderAndReceiver(messageMetaData){
        if( !messageMetaData){
            return;
        }
        const {uuid, recipient, sender} = messageMetaData;

        if(!this.seenMessageIds.has(uuid)){
            this.seenMessageIds.add(uuid);
            this.addToFrequencyMap(recipient, this.receivers)
            this.addToFrequencyMap(sender, this.senders);
        }
    }

    addToFrequencyMap(element, frequencyMap){
        if( element in frequencyMap){
            frequencyMap[element]++
        }else{
            frequencyMap[element] = 1;
        }
    }

    clearAll()
    {
        this.senders = {}
        this.receivers = {}
        this.seenMessageIds.clear();
    }

    getMapByLabel(label){
        if(label === 'requestFromContent'){
            return this.senders;
        }else if( label === 'requestToContent'){
            return this.receivers
        }else{
            throw Error("Unsupported label");
        }
    }
}

export default SenderReceiversFrequencyCounter;
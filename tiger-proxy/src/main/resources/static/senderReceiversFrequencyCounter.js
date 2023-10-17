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

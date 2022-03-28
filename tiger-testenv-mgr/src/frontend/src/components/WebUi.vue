<template>
  <div class="container">
    <div class="row">
      <div class="col-md-4">
        <h4>Serverliste:</h4>
        <ServerStatus :serverStatusData="serverStatus"/>
      </div>
      <div class="col-md-4">
        <h4>ServerUpdates:</h4>
        <ServerUpdateStatus :serverUpdateStatus="serverUpdateStatus"/>
      </div>
      <div class="col-md-4">
        <h4>Banner Messages:</h4>
        <BannerMessage :bannerData="bannerData"/>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {onMounted, Ref, ref} from 'vue'
import SockJS from "sockjs-client";
import Stomp, {Client, Frame, Message} from "webstomp-client";
import TigerEnvStatusDto from "@/types/TigerEnvStatusDto";
import TigerServerStatusUpdateDto from "@/types/TigerServerStatusUpdateDto";
import MessageUpdateDto from "@/types/MessageUpdateDto";
import TestEnvStatusDto from "@/types/TestEnvStatusDto";
import BannerMessage from "@/components/testsuite/BannerMessage.vue";
import ServerUpdateStatus from "@/components/server/ServerUpdateStatus.vue";
import ServerStatus from "@/components/server/ServerStatus.vue";

let baseURL = process.env.BASE_URL
let socket: any
let stompClient: Client
let bannerData: Ref<MessageUpdateDto[]> = ref([]);
let serverUpdateStatus: Ref<Map<string, TigerServerStatusUpdateDto> | null> = ref(null)
let serverStatus: Ref<TigerEnvStatusDto | null> = ref(null)

function connectToWebSocket() {
  socket = new SockJS(baseURL + "testEnv");
  stompClient = Stomp.over(socket);
  stompClient.connect(
      {},
      () => {
        stompClient.subscribe(baseURL + "topic/envStatus", (tick: Message) => {
          let messages: TestEnvStatusDto = JSON.parse(tick.body);
          let serverMessage: Map<string, TigerServerStatusUpdateDto> =
              messages.servers;
          let bannerMessage: MessageUpdateDto = messages.message;
          if (serverMessage === null) {
            // banner message
            bannerData.value.push(bannerMessage);
          } else {
            // server update
            serverUpdateStatus.value = serverMessage;
          }
        });
      },
      (error: Frame | CloseEvent) => {
        console.log(error);
      }
  );
}

function fetchInitialServerStatus() {
  fetch(baseURL + "status")
  .then((response) => response.text())
  .then((data) => {
    serverStatus.value = JSON.parse(data);
  });
}

onMounted(() => {
  connectToWebSocket();
  fetchInitialServerStatus();
})
</script>
<style></style>

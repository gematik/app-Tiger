<template>
  <div>
    <div class="row" style="margin-top: 30px">
      <div class="col-md-4" style="margin-left: 10px">
        <ServerStatus :serverStatusData="serverStatus" />
      </div>
      <div class="col-md-7">
        <h2>Banner Messages:</h2>
        <BannerMessage :bannerData="bannerData" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {onMounted, Ref, ref} from "vue";
import SockJS from "sockjs-client";
import Stomp, {Client, Frame, Message} from "webstomp-client";
import TigerEnvStatusDto from "@/types/TigerEnvStatusDto";
import TigerServerStatusUpdateDto from "@/types/TigerServerStatusUpdateDto";
import MessageUpdateDto from "@/types/MessageUpdateDto";
import TestEnvStatusDto from "@/types/TestEnvStatusDto";
import BannerMessage from "@/components/testsuite/BannerMessage.vue";
import ServerStatus from "@/components/server/ServerStatus.vue";
import TigerServerStatusDto from "@/types/TigerServerStatusDto";

let baseURL = process.env.BASE_URL;
let socket: any;
let stompClient: Client;
let bannerData: Ref<MessageUpdateDto[]> = ref([]);
let serverUpdateStatus: Ref<Map<string, TigerServerStatusUpdateDto> | null> =
  ref(null);
let serverStatus: Ref<TigerEnvStatusDto | null> = ref(null);

function connectToWebSocket() {
  socket = new SockJS(baseURL + "testEnv");
  stompClient = Stomp.over(socket);
  stompClient.connect(
    {},
    () => {
      stompClient.subscribe(baseURL + "topic/envStatus", (tick: Message) => {
        let messages: TestEnvStatusDto = JSON.parse(tick.body);
        let serverMessage: Map<string, TigerServerStatusUpdateDto> = null;
        if (messages.servers !== null) {
          serverMessage = new Map<string, TigerServerStatusUpdateDto>(
            Object.entries(messages.servers)
          );
        }
        let bannerMessage: MessageUpdateDto = messages.message;
        if (serverMessage === null) {
          // banner message
          bannerData.value.push(bannerMessage);
        } else {
          // server update
          serverUpdateStatus.value = serverMessage;
          updateServerStatus(serverMessage);
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
      let servers: Map<string, TigerServerStatusDto> = null;
      if (serverStatus.value?.servers !== null) {
        servers = new Map<string, TigerServerStatusDto>(
          Object.entries(serverStatus.value?.servers)
        );
        serverStatus.value.servers = servers;
      }
    });
}

function updateServerStatus(update: Map<string, TigerServerStatusUpdateDto>) {
  if (
    serverStatus.value !== null &&
    serverStatus.value.servers !== null &&
    update !== null
  ) {
    update.forEach((value, key) => {
      if (serverStatus.value.servers.get(key) !== null) {
        // update
        serverStatus.value.servers.get(key).statusMessage = value.statusMessage;
        serverStatus.value.servers.get(key).statusUpdates.push(value.statusMessage);
      } else {
        // add
        serverStatus.value?.servers.set(key, value);
      }
    });
  }
}

onMounted(() => {
  fetchInitialServerStatus();
  connectToWebSocket();
});
</script>
<style>
@import "~bootstrap/dist/css/bootstrap.min.css";
@import url("https://cdn.jsdelivr.net/npm/bootstrap-icons@1.8.1/font/bootstrap-icons.css");

</style>

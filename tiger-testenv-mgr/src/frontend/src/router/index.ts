import {createRouter, createWebHistory} from "vue-router";
import TestProjekt from "@/pages/TestProjekt.vue";
import Kontakt from "@/pages/Kontakt.vue";

const router = createRouter({
  // Optionen
  history: createWebHistory(),
  routes: [
    {
      path: "/",
      component: TestProjekt,
    },
    {
      path: "/about",
      component: Kontakt,
    },
  ],
});

export default router;

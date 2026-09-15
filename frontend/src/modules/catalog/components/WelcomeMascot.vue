<script setup lang="ts">
defineProps<{ greeting: boolean }>();
</script>

<template>
  <svg
    class="welcome-mascot"
    :class="{ 'is-greeting': greeting }"
    viewBox="0 0 180 165"
    fill="none"
    aria-hidden="true"
  >
    <ellipse
      class="welcome-mascot__shadow"
      cx="91"
      cy="149"
      rx="41"
      ry="6"
      fill="currentColor"
      opacity=".09"
    />
    <g class="welcome-mascot__float">
      <path
        d="M60 130l-7 13M117 130l9 13"
        stroke="currentColor"
        stroke-width="12"
        stroke-linecap="round"
      />
      <path
        class="welcome-mascot__hand"
        d="M134 91q24-8 17-26"
        stroke="currentColor"
        stroke-width="12"
        stroke-linecap="round"
      />
      <path
        d="M44 95q-17 7-19-6"
        stroke="currentColor"
        stroke-width="11"
        stroke-linecap="round"
      />
      <g class="welcome-mascot__head">
        <path
          d="M42 72C32 48 36 22 55 23c14 1 18 16 20 22 8-4 18-4 27-1 6-21 15-27 26-22 16 6 15 29 11 49 16 13 19 42 3 58-15 16-83 18-100 0-15-17-13-39 0-57Z"
          fill="currentColor"
        />
        <g class="welcome-mascot__face">
          <g class="welcome-mascot__eyes" fill="#faf9f6">
            <ellipse cx="68" cy="85" rx="12" ry="16" />
            <ellipse cx="109" cy="85" rx="12" ry="16" />
            <g class="welcome-mascot__pupils" fill="currentColor">
              <ellipse cx="70" cy="86" rx="5.5" ry="8" />
              <ellipse cx="111" cy="86" rx="5.5" ry="8" />
            </g>
          </g>
          <path
            :d="greeting ? 'M77 108q12 17 24 0Z' : 'M80 110q10 8 20-2'"
            stroke="#faf9f6"
            stroke-width="4"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </g>
      </g>
    </g>
  </svg>
</template>

<style scoped>
.welcome-mascot {
  width: 100%;
  height: auto;
  overflow: visible;
  color: #393c32;
}
.welcome-mascot__float {
  transform-origin: 90px 130px;
  animation: mascot-breathe 4.8s ease-in-out infinite;
}
.welcome-mascot__head {
  transform-origin: 90px 112px;
  transform: rotate(calc(var(--look-x, 0) * 7deg));
  transition: transform 160ms ease-out;
}
.welcome-mascot__face {
  transform: translate(
    calc(var(--look-x, 0) * 4px),
    calc(var(--look-y, 0) * 3px)
  );
  transition: transform 100ms ease-out;
}
.welcome-mascot__pupils {
  transform: translate(
    calc(var(--look-x, 0) * 4px),
    calc(var(--look-y, 0) * 4px)
  );
  transition: transform 100ms ease-out;
}
.welcome-mascot__eyes {
  transform-origin: 90px 85px;
  animation: mascot-blink 6s ease-in-out infinite;
}
.welcome-mascot__hand {
  transform-origin: 135px 92px;
}
.is-greeting .welcome-mascot__hand {
  animation: mascot-wave 380ms ease-in-out 3;
}
.is-greeting .welcome-mascot__float {
  animation: mascot-hello 600ms ease-in-out 2;
}
@keyframes mascot-breathe {
  0%,
  100% {
    transform: translateY(0) rotate(-2deg);
  }
  50% {
    transform: translateY(-5px) rotate(2deg);
  }
}
@keyframes mascot-blink {
  0%,
  43%,
  47%,
  100% {
    transform: scaleY(1);
  }
  45% {
    transform: scaleY(0.1);
  }
}
@keyframes mascot-wave {
  0%,
  100% {
    transform: rotate(-10deg);
  }
  50% {
    transform: rotate(25deg);
  }
}
@keyframes mascot-hello {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px) rotate(-4deg);
  }
}
@media (prefers-reduced-motion: reduce) {
  .welcome-mascot * {
    animation: none !important;
    transition: none !important;
  }
}
</style>

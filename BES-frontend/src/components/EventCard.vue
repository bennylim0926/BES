<script setup>
import { defineProps, defineEmits } from 'vue';

const props = defineProps({
    buttonName: {
        type: String,
        required: true,
        default: ()=>("")
    }
})

const emit = defineEmits(["onClick"])
</script>

<template>
    <div class="card1 flex justify-center items-center shadow-lg"
        @click="emit('onClick')">
        <h3 class="">
            {{props.buttonName}}
        </h3>
        <div class="go-corner" href="#">
    </div>
    </div>
</template>

<style>
go-corner {
  display: flex;
  align-items: center;
  justify-content: center;
  position: absolute;
  width: 32px;
  height: 32px;
  overflow: hidden;
  top: 0;
  right: 0;
  background-color: #ff8904;
  border-radius: 0 4px 0 32px;
}

.go-arrow {
  margin-top: -4px;
  margin-right: -4px;
  color: white;
  font-family: courier, sans;
}

h3 {
  color: #262626;
  font-size: 24px;
  line-height: 24px;
  font-weight: 700;
  margin-bottom: 4px;
}

.card1 {
  display: block;
  position: relative;
  width: 262px;
  background-color: #fffaf5;
  border-radius: 4px;
  padding: 32px 24px;
  margin: 12px;
  text-decoration: none;
  z-index: 0;
  overflow: hidden;

  &:before {
    content: "";
    position: absolute;
    z-index: -1;
    top: -16px;
    right: -16px;
    background: #ff8904;
    height: 32px;
    width: 32px;
    border-radius: 32px;
    transform: scale(1);
    transform-origin: 50% 50%;
    transition: transform 0.25s ease-out;
  }

  &:hover:before {
    transform: scale(21);
  }
}

.card1:hover {
  p {
    transition: all 0.3s ease-out;
    color: rgba(255, 255, 255, 0.8);
  }
  h3 {
    transition: all 0.3s ease-out;
    color: #ffffff;
  }
}
</style>
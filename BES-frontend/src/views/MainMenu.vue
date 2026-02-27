<script setup>
</script>

<template>
  <div class="flex flex-col justify-center items-center px-6 py-10 max-w-4xl mx-auto text-black">
    <h1 class="text-3xl font-bold mb-8">How to Use</h1>

    <!-- Before Event Day -->
    <section class="w-full mb-10">
      <h2 class="text-2xl font-semibold mb-4">Before Event Day</h2>
      <ol class="list-decimal list-inside space-y-2">
        <li>Each folder should contain one response form.</li>
        <li>Each response should contain at least: <span class="font-medium">Name, Category/Categories, Email</span>.</li>
        <li>Go to <span class="italic">Events</span> and choose one.</li>
        <li>The first table shows participant breakdown for each genre.</li>
      </ol>

      <div class="mt-6">
        <h3 class="text-xl font-medium mb-2">If there is a record of this event:</h3>
        <p class="ml-4">1. A table will be shown with participants and categories joined.</p>
      </div>

      <div class="mt-6">
        <h3 class="text-xl font-medium mb-2">Else:</h3>
        <ol class="list-decimal list-inside space-y-2 ml-4">
          <li>Choose categories.</li>
          <li>Name the judges.</li>
          <li>Insert a record in the database.</li>
          <ul class="list-disc list-inside ml-6">
            <li>A new column is inserted into the response form, ticked after you verify it.</li>
          </ul>
          <li>Refresh the database once you verify payment from Google response.</li>
          <ul class="list-disc list-inside ml-6">
            <li>Participants should receive email with QR codes for each category they signed up for.</li>
          </ul>
        </ol>
      </div>

      <div class="mt-6">
        <h3 class="text-xl font-medium mb-2">
          If any category is judged by different judges (e.g., PopCity or Culture Circle format):
        </h3>
        <ol class="list-decimal list-inside space-y-2 ml-4">
          <li>Go to <span class="italic">Update Event Details</span>.</li>
          <li>Assign judges to each participant and press the update button.</li>
        </ol>
      </div>
    </section>

    <!-- On Event Day -->
    <section class="w-full mb-10">
      <h2 class="text-2xl font-semibold mb-4">On the Event Day</h2>

      <div class="mb-6">
        <h3 class="text-xl font-medium mb-2">Participants</h3>
        <p class="ml-4">1. Present the QR to the registration counter.</p>
      </div>

      <div class="mb-6">
        <h3 class="text-xl font-medium mb-2">Event Organiser</h3>
        <ol class="list-decimal list-inside space-y-2 ml-4">
          <li>Prepare one screen to display audition number on <span class="italic">Audition Number</span>.</li>
          <li>Scan QR → The audition number + category will be displayed.</li>
          <li>Give wrist tag with number.</li>
        </ol>
      </div>

      <div class="mb-6">
        <h3 class="text-xl font-medium mb-2">Emcee</h3>
        <ol class="list-decimal list-inside space-y-2 ml-4">
          <li>Go to <span class="italic">Audition List</span>, update the filter.</li>
          <li>Use the timer for countdown.</li>
        </ol>
      </div>

      <div class="mb-6">
        <h3 class="text-xl font-medium mb-2">Judge</h3>
        <ol class="list-decimal list-inside space-y-2 ml-4">
          <li>Go to <span class="italic">Audition List</span>, update the filter.</li>
          <li>Make sure <span class="italic">Current Judge By</span> is selected.</li>
          <li>Give score and update.</li>
        </ol>
      </div>
    </section>

    <!-- When Audition Ends -->
    <section class="w-full">
      <h2 class="text-2xl font-semibold mb-4">When Audition Ends</h2>
      <ol class="list-decimal list-inside space-y-2">
        <li>Go to <span class="italic">Score</span> to get your top-n participants.</li>
        <li>If judges were assigned previously, choose <span class="italic">By Judge</span>.</li>
      </ol>
    </section>
  </div>
</template>

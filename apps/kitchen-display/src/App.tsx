import { Track } from "@railbite/shared";
import React from "react";

function App() {
  return (
    <div>
      <h1>RailBite Kitchen Display</h1>
      <p>Track types: {Track.COOK}, {Track.READY}</p>
    </div>
  );
}

export default App;


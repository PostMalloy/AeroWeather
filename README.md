<p align="center">
  <img src="https://github.com/PostMalloy/AeroWeather/blob/master/img/Logo.png" width="100%" />
</p>
<video width="320" height="240" controls>
  <source src="https://github.com/PostMalloy/AeroWeather/blob/master/img/aeroweather.mp4" type="video/mp4">
</video>
## Description
Aeroweather is a minecraft mod that provides a lightweight and configurable wind system to minecraft. 

## Features

Wind varies in intensity and direction in a semi-random pattern, and wind speed varies with y-level. Particles meander around the player to indicate the wind direction. Rain and thunderstorms can produce higher winds (though not always) and yield more erratic conditions. Simple wind gusts are also modeled. 

Create windmills can now be affected by local wind speed, both in speed (and therefore stress) and in direction (configurable, default off). Now it's actually advantageous to build your windmills on higher ground.


Perhaps most importantly, wind will impart force on Create Aeronautics contraptions. The force of the wind is calculated via the cross-sectional area of the contraption that faces the wind, and scaled by the proportion of balloon, levitite, or wing-like blocks on your contraption to the proportion of non-lifting blocks. This means that contraptions without any of these blocks (such as cars, mining machines, etc) are unaffected by the wind, while contraptions like hot air balloons are most affected by it. 

<p align="center">
  <img src="https://github.com/PostMalloy/AeroWeather/blob/master/img/ex1.gif" width="50%" />
</p>

The mod now includes a few utility blocks that can help your aeronautics creations to perform well with the new wind feature. Wind vanes (zinc and brass variants) point in the direction of the wind, and the brass vane outputs a redstone signal with variable strength in the direction(s) of the wind. The stronger the wind, the stronger the signal. Both blocks also work when held!

<p align="center">
  <img src="https://github.com/PostMalloy/AeroWeather/blob/master/img/ex2.gif" width="50%" />
</p>

## Commands

`/aeroweather wind info` - displays information on wind speed, direction, modeled intensity, and height-scaled intensity from a scale of 0-100.

`/aeroweather wind *cardinal direction* *intensity*` - sets the wind speed and modeled intensity. Persists until wind simulation is reset via `/aeroweather wind reset`.

## Configuration 

The mod was designed to be as configurable as possible. 

* All wind parameters are easily modifiable, including the math required for the semi-random walk cycles for wind speed and direction.

* Change the boost and max wind speed settings for all vanilla weather patterns.

* Particle behavior can be modified, toggled, or set to only appear near contraptions with lifting properties. 

* Aeronautics force calculations and maximum forces are also configurable.

## Compatibility

* [Particle Rain](https://modrinth.com/mod/particle-rain): particles are wind-driven and move in the direction of the wind.

## Future Features

* Wind sounds that scale based on local wind speed

* Biome influence - Wind should be stronger in a plains or badlands biome, compared to a forest or taiga. This influence will decay with y-level.

* More items and blocks to interact with the wind feature (handheld/placeable anemometer)

## Out of Scope - Will Not Be Implemented

* Integration with other weather mods (E.G. Protomanly's Weather)


## AI Disclosure

All concepts, art & assets, default configuations for mod parameters, human-readible text and mod descriptions were created by me without the assistance of AI.

There is AI-generated code in this mod. I believe strongly in limiting AI usage to code only and will never utilize AI for creating art. I am not a strong java developer, I create modpacks as a hobby and designed this mod for personal use.


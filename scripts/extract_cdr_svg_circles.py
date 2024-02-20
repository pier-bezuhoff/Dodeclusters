#!/usr/bin/env python

# This script is used to convert .cdr that contains only circles into .ddc (format for Dodeclusters)
# Prerequisite: convert .cdr to single-page .svg via LibreOffice Draw (tested on v. 7.6.4.1)
import xml.etree.ElementTree as ET
from ast import literal_eval
import json

input_path = "/home/pierbezuhoff/Downloads/dracon.svg"
output_path = "/home/pierbezuhoff/Downloads/exported-cluster.ddc"
tree = ET.parse(input_path)
root = tree.getroot()
viewBox = root.get('viewBox').split()
width = int(viewBox[-2])
height = int(viewBox[-1])
group = root[-1][0][0][0][0][0]
circles = []
for shape in group:
    if shape.get('class') == "com.sun.star.drawing.ClosedBezierShape":
        circle_id = shape[0].get('id')
        bounding_rect = shape[0][0]
        path1 = shape[0][1]
        left = int(bounding_rect.get('x'))
        top = int(bounding_rect.get('y'))
        w = int(bounding_rect.get('width'))
        h = int(bounding_rect.get('height'))
        x = left + w/2
        y = top + h/2
        R = w/2 # sometimes width and height differ by 1px, idk why
        fill = path1.get('fill')
        try:
            (r,g,b) = literal_eval(fill[3:])
            css_color = '#%02x%02x%02x' % (r,g,b)
            # reference: https://androidx.tech/artifacts/compose.ui/ui-graphics/1.6.1-source/commonMain/androidx/compose/ui/graphics/Color.kt.html
            # line #420
            long_color = ((0xff << 24 | r << 16 | g << 8 | b) & 0xffffffff) << 32
            color = css_color
        except ValueError:
            print(f"oof #{circle_id}: fill={fill}")
            color = None
        circles.append(dict(x=x, y=y, r=R, color=color))
#print(circles)
scale_down = 15
shift_x = 100
shift_y = 0
circles_entry = [ {'x': c['x']/scale_down - shift_x, 'y': c['y']/scale_down - shift_y, 'radius': c['r']/scale_down} for c in circles ]
parts_entry = [ {'insides': [ix], 'outsides': [], 'fillColor': circles[ix]['color'] } for ix in range(len(circles)) ]
cluster = {
    'type': 'Cluster',
    'circles': circles_entry,
    'parts': parts_entry
}
with open(output_path, 'w') as file:
    json.dump(cluster, file, allow_nan=False)
print(f"{len(circles_entry)} circles written")

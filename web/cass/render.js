function dopopulate() {
   addoption(">>> Please choose a drive <<<");
   addoption("eaec3f9d-28d9-4b67-b08b-005594b4140");
   addoption("a159ab17-e14f-446f-987d-61c0a233078");
}
function doselect(value) {
  if (window.myDoughnut !== undefined) window.myDoughnut.destroy();
  if (window.myDoughnut2 !== undefined) window.myDoughnut2.destroy();
   if (value == "eaec3f9d-28d9-4b67-b08b-005594b4140") render_eaec3f9d_28d9_4b67_b08b_005594b4140();
   if (value == "a159ab17-e14f-446f-987d-61c0a233078") render_a159ab17_e14f_446f_987d_61c0a233078();
}
function render_eaec3f9d_28d9_4b67_b08b_005594b4140() {
   window.myDoughnut = new Chart(ctx).Doughnut(doughnutData_eaec3f9d_28d9_4b67_b08b_005594b4140,
        {
        responsive: true,
        percentageInnerCutout: 40,
        animationEasing: "easeOutQuart"
        }
   );
   window.myDoughnut2 = new Chart(ctx2).Doughnut(doughnutData_eaec3f9d_28d9_4b67_b08b_005594b4140_cnt,
        {
        responsive: true,
        percentageInnerCutout: 40,
        animationEasing: "easeOutQuart"
        }
   );
}
function render_a159ab17_e14f_446f_987d_61c0a233078() {
   window.myDoughnut = new Chart(ctx).Doughnut(doughnutData_a159ab17_e14f_446f_987d_61c0a233078,
        {
        responsive: true,
        percentageInnerCutout: 40,
        animationEasing: "easeOutQuart"
        }
   );
   window.myDoughnut2 = new Chart(ctx2).Doughnut(doughnutData_a159ab17_e14f_446f_987d_61c0a233078_cnt,
        {
        responsive: true,
        percentageInnerCutout: 40,
        animationEasing: "easeOutQuart"
        }
   );
}
